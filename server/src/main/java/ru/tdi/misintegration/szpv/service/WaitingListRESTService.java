package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.tdi.misintegration.szpv.Utils;
import ru.tdi.misintegration.szpv.config.Lpu;
import ru.tdi.misintegration.szpv.config.MisConfig;
import ru.tdi.misintegration.szpv.config.SoapConnector;
import ru.tdi.misintegration.szpv.model.*;
import ru.tdi.misintegration.szpv.ws.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WaitingListRESTService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplateNP;

    @Autowired
    MisConfig misConfig;

    @Autowired
    UserService userService;

    @Autowired
    SoapConnector soapConnector;

    @Autowired
    AppointmentService appService;

    @Autowired
    NotifyAsyncService notifyAsyncService;

    @Autowired
    QueueInformationService queueService;

    @Autowired
    WaitingListRESTService wtService;

    @Autowired
    WaitingListAsyncService wlAsyncService;

    @Cacheable(value="waitingList", key = "#root.methodName")
    public List<PARequestRes> getActivePARequests() {
        List<PARequestRes> res = new ArrayList<>();
        for (Lpu lpu : misConfig.getLpus()) {
            res.addAll(getActivePARequests(lpu.getRemoteId()));
        }
        return res;
    }

   private PARequestRes convertPARequest(ActivePARequestInfo r) {
       SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy");

       PARequestRes par = new PARequestRes();
       PatientRes pat = new PatientRes();
       pat.setSurname(r.getPARequestPatient().getLastName());
       pat.setName(r.getPARequestPatient().getFirstName());
       pat.setSecondName(r.getPARequestPatient().getMiddleName());
       pat.setBirthDate(Utils.fromXMLDate(r.getPARequestPatient().getBirthDate()));
       pat.setAddInfo(r.getPARequestPatient().getPARequestPatientContacts().getAdditionalInformation());
       pat.setPhone(r.getPARequestPatient().getPARequestPatientContacts().getPhone());
       pat.setEmail(r.getPARequestPatient().getPARequestPatientContacts().getEmail());
       pat.setIdPat(r.getPARequestPatient().getIdPatient());

       if (checkPatient(pat.getIdPat(),pat.getSurname(),pat.getBirthDate())) {
           par.setInvalidClient(false);
       } else {
           par.setInvalidClient(true);
       }

       par.setClaim(r.getPARequestInfo().getClaim());
       par.setCreated(Utils.fromXMLDate(r.getCreatedDate()));
       par.setIdDoc(r.getPARequest().getIdDoc());
       par.setNameDoc(r.getPARequest().getNameDoc());
       par.setIdSpec(r.getPARequest().getIdSpeciality());
       par.setNameSpec(r.getPARequest().getNameSpeciality());
       par.setIdLpu(r.getPARequest().getIdLpu());
       par.setLpuName(r.getPARequest().getIdLpu());
       Lpu lpu = misConfig.getEgisLpuMap().get(Integer.valueOf(par.getIdLpu()));
       if (lpu!=null) par.setLpuName(lpu.getName());
       par.setIdPar(r.getIdPar());
       par.setInfo(r.getPARequestInfo().getInfo());
       par.setClaim(r.getPARequestInfo().getClaim());
       par.setSource(r.getPASourceCreated());
       par.setStatus(1);
       par.setPatient(pat);
       String period = "";
       for(PARequestInterval inter : r.getPreferredIntervals().getPARequestInterval()) {
           period = period + sdf.format(Utils.fromXMLDate(inter.getStartDate()))+"-"+sdf.format(Utils.fromXMLDate(inter.getEndDate()))+" ";
       }
       par.setPeriod(period);
       return par;
   }

    public List<PARequestRes> getActivePARequests(Integer lpuId) {
        SearchActivePARequests req = new SearchActivePARequests();
        req.setGuid(misConfig.getGuid());

        SearchActivePARequestsRequest filter  = new SearchActivePARequestsRequest();
        filter.setIdLpu(lpuId.toString());
        req.setFilter(filter);

        SearchActivePARequestsResponse resp = (SearchActivePARequestsResponse) soapConnector.callWebService(misConfig.getWebService(), "SearchActivePARequests", req);

        List<ActivePARequestInfo> reqList = resp.getSearchActivePARequestsResult().getActivePARequests().getActivePARequestInfo();

        List<PARequestRes> list = reqList.stream().map(r -> convertPARequest(r)).collect(Collectors.toList());

        list.stream().forEach((par)->{
            try {
                wtService.logRegisterPARequest(par);
            } catch (Exception ex) {}
        });

        return list;
    }

    public List<PARequestRes> searchPARequests(Integer idPat) {
        SearchPARequests req = new SearchPARequests();
        req.setGuid(misConfig.getGuid());

        SearchPARequestsRequest rReq = new SearchPARequestsRequest();
        req.setFilter(rReq);

        ArrayOfIdInfo arr = new ArrayOfIdInfo();
        rReq.setIdInfos(arr);

        arr.getIdInfo().addAll(
            misConfig.getLpus().stream().map(lpu -> {
                IdInfo info = new IdInfo();
                info.setIdLpu(lpu.getRemoteId().toString());
                info.setIdPatient(idPat.toString());
                return info;
            }).collect(Collectors.toList())
        );

        SearchPARequestsResponse resp = (SearchPARequestsResponse) soapConnector.callWebService(misConfig.getWebService(), "SearchPARequests", req);

        List<SearchPARequestInfo> reqList = resp.getSearchPARequestsResult().getPARequests().getSearchPARequestInfo();

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy");

        return reqList.stream().map(r -> {
            PARequestRes par = convertPARequest(r);
            if (r.getPARequestDeactivationInfo()!=null) {
                par.setDeactivateDate(Utils.fromXMLDate(r.getPARequestDeactivationInfo().getDeactivationDate()));
                par.setDeactivateReason(r.getPARequestDeactivationInfo().getDeactivationReason());
                par.setDeactivateComment(r.getPARequestDeactivationInfo().getDeactivationComment());
            }
            par.setStatus(r.getPARequestStatus());
            return par;
        }).collect(Collectors.toList());
    }


    public List<DoctorRes> getDoctors(String specId) {
        return jdbcTemplate.query("SELECT p.id, p.name, org.name as dept from vrbPerson p " +
                "left join p106.TopOrgs org on org.id=p.orgStructure_id " +
                "where p.retireDate IS NULL and p.speciality_id=? order by org.name, p.name", new Object[]{specId}, (rs, i) -> {
            DoctorRes d = new DoctorRes();
            d.setId(rs.getInt("id"));
            d.setName(rs.getString("name"));
            d.setDept(rs.getString("dept"));
            return d;
        } );
    }


    public List<Date> getAvailableDates(String doctId, Date dtBegin, Date dtEnd) {
        String query = "SELECT DATE FROM ( " +
                "SELECT DATE, SUM(free) AS free, SUM(totalT) AS total " +
                "FROM ( " +
                "SELECT DATE(Event.setDate) AS DATE, 1 AS totalT, IF(atQueue.value IS NULL,1,0) AS free " +
                "FROM Action " +
                "LEFT JOIN ActionType ON ActionType.id = Action.actionType_id " +
                "LEFT JOIN Event ON (Event.id = Action.event_id AND Event.eventType_id=:eventTypeId) " +
                "LEFT JOIN Person ON Person.id = Event.setPerson_id " +
                "LEFT JOIN Action aT ON aT.id=Action.id " +
                "LEFT JOIN ActionProperty atP ON (atP.action_id=aT.id AND atP.type_id=:actionPTimesId) " +
                "LEFT JOIN ActionProperty_Time atTime ON atTime.id=atP.id " +
                "LEFT JOIN ActionProperty aqP ON (aqP.action_id=aT.id AND aqP.type_id=:actionPQueueId) " +
                "LEFT JOIN ActionProperty_Action atQueue ON (atQueue.id=aqP.id AND atQueue.`index`=atTime.`index`) " +
                "LEFT JOIN Action qA ON qA.id=atQueue.value " +
                "WHERE Event.deleted=0 AND Action.deleted=0 AND atTime.value IS NOT NULL AND Action.actionType_id=:actionTypeId " +
                "AND Event.setPerson_id=:doctId " +
                "AND Event.setDate between :dtBegin and :dtEnd  AND Event.setDate>=CURRENT_DATE() " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL  " +
                ") AS T " +
                "GROUP BY DATE) AS T2 " +
                "WHERE free > 0";

        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();
        parameters.addValue("doctId", doctId);
        parameters.addValue("dtBegin", dtBegin);
        parameters.addValue("dtEnd", dtEnd);

        return jdbcTemplateNP.query(query, parameters, (resultSet, i) -> resultSet.getDate("date"));

    }


    public List<QueueItem> getQueue(Integer docId, Date date) {

        MapSqlParameterSource param = misConfig.getCommonSQLParameters();
        param.addValue("doctId", docId);
        param.addValue("dt", date);

        String queryActionId = "select Action.id from Action " +
                "LEFT JOIN Event ON Event.id = Action.event_id " +
                "where " +
                "Event.deleted=0 AND Action.deleted=0 AND  Event.eventType_id=:eventTypeId " +
                "AND Action.actionType_id=:actionTypeId " +
                "AND Event.setPerson_id = :doctId AND Event.setDate = :dt ";

        Integer actionId = jdbcTemplateNP.queryForObject(queryActionId, param, Integer.class);

        appService.createQueueRecords(actionId, true);

        String query = "SELECT DATE(Event.setDate) AS qDate, atTime.value as qTime, atQueue.id as qId, atQueue.`index`  as qIdx," +
                "CONCAT(CAST(cl.id as CHAR),' ',cl.lastName,' ',left(cl.firstName,1),'.',left(cl.patrName,1),'.') as pat, atQueue.value as qAction " +
                "FROM Action " +
                "LEFT JOIN ActionType ON ActionType.id = Action.actionType_id " +
                "LEFT JOIN Event ON (Event.id = Action.event_id AND Event.eventType_id=:eventTypeId) " +
                "LEFT JOIN Person ON Person.id = Event.setPerson_id " +
                "LEFT JOIN Action aT ON aT.id=Action.id " +
                "LEFT JOIN ActionProperty atP ON (atP.action_id=aT.id AND atP.type_id=:actionPTimesId) " +
                "LEFT JOIN ActionProperty_Time atTime ON atTime.id=atP.id " +
                "LEFT JOIN ActionProperty aqP ON (aqP.action_id=aT.id AND aqP.type_id=:actionPQueueId) " +
                "LEFT JOIN ActionProperty_Action atQueue ON (atQueue.id=aqP.id AND atQueue.`index`=atTime.`index`) " +
                "LEFT JOIN Action qA ON qA.id=atQueue.value " +
                "LEFT JOIN Event qE ON qE.id=qA.event_id " +
                "LEFT JOIN Client cl ON cl.id=qE.client_id " +
                "WHERE Event.deleted=0 AND Action.deleted=0 AND atTime.value IS NOT NULL AND Action.actionType_id=:actionTypeId " +
                "AND Event.setPerson_id=:doctId " +
                "AND Event.setDate=:dt  " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL " +
                "ORDER BY DATE(Event.setDate), atTime.value";

       return jdbcTemplateNP.query(query, param, (rs, i) -> {
            QueueItem qi = new QueueItem();
            qi.setClientName(rs.getString("pat"));
            qi.setQueueId(rs.getInt("qId"));
            qi.setQueueIdx(rs.getInt("qIdx"));
            qi.setQueueAction(rs.getInt("qAction"));
            qi.setTime(rs.getString("qTime"));
            qi.setFree(qi.getClientName()==null);
            return qi;
        });
    }

    public Boolean cancelPA(String idPar, Integer reason, String comment) {
        CancelPARequestRequest req = new CancelPARequestRequest();
        CancelPARequest r = new CancelPARequest();
        r.setRequest(req);
        r.setGuid(misConfig.getGuid());

        req.setIdPar(idPar);
        req.setDeactivationReason(reason);
        req.setDeactivationComment(comment);
        req.setPASource(3);

        CancelPARequestResponse resp = (CancelPARequestResponse) soapConnector.callWebService(misConfig.getWebService(), "CancelPARequest", r);

        try {
            wtService.logRemovePARequest(idPar, reason, comment);
        } catch (Exception ex) {}

        wlAsyncService.removePARfromCache(idPar);
        return resp.getCancelPARequestResult().isCancellationResult();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Boolean cancelQueue(QueueItem queue, Integer idUser) {
        if (jdbcTemplate.update("UPDATE ActionProperty_Action set value=NULL where id=? and `index`=? and value=?", queue.getQueueId(), queue.getQueueIdx(), queue.getQueueAction())!=1) return false;
        jdbcTemplate.update("UPDATE Action set deleted=1, modifyDatetime=NOW(), modifyPerson_id=? where id=?", idUser, queue.getQueueAction() );
        return true;
    }

    public Boolean setAppointment(String idPar, Integer idPat, QueueItem queueItem, Integer idUser) {
        Integer resId = wtService.setAppointmentWL(idPar, idPat, queueItem, idUser);
        notifyAsyncService.sendAppointmentNotificationAsync(resId);
        if (resId!=null) wlAsyncService.removePARfromCache(idPar);
        return resId!=null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer setAppointmentWL(String idPar, Integer idPat, QueueItem queueItem, Integer idUser) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("eventTypeId", misConfig.getQEventType());
        parameters.addValue("actionTypeId", misConfig.getQActionType());
        parameters.addValue("officeTypeId", misConfig.getOfficeAPType());
        parameters.addValue("actionPTimesId", misConfig.getTAPType());
        parameters.addValue("actionPQueueId", misConfig.getQAPType());
        parameters.addValue("queueId", queueItem.getQueueId());
        parameters.addValue("queueIdx", queueItem.getQueueIdx());


        String queryActionId = "select Action.id from ActionProperty_Action apa " +
                "LEFT JOIN ActionProperty ap ON apa.id=ap.id " +
                "LEFT JOIN Action on ap.action_id=Action.id " +
                "LEFT JOIN Event ON Event.id = Action.event_id " +
                "where " +
                "Event.deleted=0 AND Action.deleted=0 AND  Event.eventType_id=:eventTypeId " +
                "AND Action.actionType_id=:actionTypeId " +
                "AND  apa.id=:queueId and apa.`index`=:queueIdx and apa.value IS NULL";

        Integer actionId = jdbcTemplateNP.queryForObject(queryActionId,parameters,Integer.class);

        Integer vLockId  = appService.getActionLock(actionId);

        if (vLockId==-1) {
            throw new RuntimeException("Талон к врачу занят/заблокирован");
        }

        Integer resId = null;

        try {
            String queryApp = "select Action.id, Event.setDate as qDate, apt.value as qTime, Event.setPerson_id as doctId from ActionProperty_Action apa " +
                    "LEFT JOIN ActionProperty ap ON apa.id=ap.id " +
                    "LEFT JOIN Action on ap.action_id=Action.id " +
                    "LEFT JOIN ActionProperty ap2 on ap2.action_id=Action.id and ap2.type_id=:actionPTimesId " +
                    "LEFT JOIN ActionProperty_Time apt ON apt.id = ap2.id  and apt.`index`=apa.`index` " +
                    "LEFT JOIN Event ON Event.id = Action.event_id " +
                    "where " +
                    "Event.deleted=0 AND Action.deleted=0 AND  Event.eventType_id=:eventTypeId " +
                    "AND ap.type_id=:actionPQueueId  " +
                    "AND Action.actionType_id=:actionTypeId " +
                    "AND  apa.id=:queueId and apa.`index`=:queueIdx and apa.value IS NULL";

            Map<String, Object> queryData;
            try {
                queryData = jdbcTemplateNP.queryForMap(queryApp, parameters);
            } catch (DataAccessException ex) {
                appService.releaseActionLock(vLockId);
                throw new RuntimeException("Талон к врачу занят/заблокирован");
            }

            Integer apQueueId = queueItem.getQueueId();
            Integer apQueueIdx = queueItem.getQueueIdx();

            String office = "";
            try {
                office = jdbcTemplate.queryForObject("select value from ActionProperty ap " +
                        "join ActionProperty_String aps on aps.id=ap.id and ap.type_id=? " +
                        "where ap.action_id=? LIMIT 1 ", new Object[]{misConfig.getOfficeAPType(), actionId}, String.class);
            } catch (DataAccessException ex) {
            }

            MapSqlParameterSource evtParam = new MapSqlParameterSource();
            evtParam.addValue("eventType", misConfig.getAppEventType());
            evtParam.addValue("orgId", misConfig.getOrgId());
            evtParam.addValue("clientId", idPat);
            evtParam.addValue("setDate", queryData.get("qDate"));
            evtParam.addValue("setTime", queryData.get("qTime"));
            evtParam.addValue("userId", idUser);
            KeyHolder evtKeyHolder = new GeneratedKeyHolder();
            jdbcTemplateNP.update("INSERT INTO Event(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id,  externalId, `order`, payStatus, note, totalCost, eventType_id, org_id, client_id, setDate, isPrimary) " +
                    "VALUES (NOW(),:userId, NOW(),:userId, '',1,0,'',0,:eventType,:orgId,:clientId,ADDTIME(CONVERT(DATE(:setDate),DATETIME),:setTime),1); ", evtParam, evtKeyHolder);

            MapSqlParameterSource actParam = new MapSqlParameterSource();
            actParam.addValue("actionType", misConfig.getAppActionType());
            actParam.addValue("eventId", evtKeyHolder.getKey().intValue());
            actParam.addValue("personId", queryData.get("doctId"));
            actParam.addValue("setDate", queryData.get("qDate"));
            actParam.addValue("setTime", queryData.get("qTime"));
            actParam.addValue("office", office);
            actParam.addValue("userId", idUser);
            actParam.addValue("note", "ЖОС УО№" + idPar);
            KeyHolder actKeyHolder = new GeneratedKeyHolder();
            jdbcTemplateNP.update("INSERT INTO Action(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, plannedEndDate, amount, account, payStatus, MKB, morphologyMKB,coordText, " +
                    "actionType_id, event_id, directionDate, status, setPerson_id, note, person_id, office) " +
                    "VALUES (NOW(),:userId, NOW(),:userId, '0000-00-00 00:00', 0, 0, 0, '','','', :actionType,:eventId, ADDTIME(CONVERT(DATE(:setDate),DATETIME),:setTime), 1, :userId, :note, :personId, :office);", actParam, actKeyHolder);


            jdbcTemplate.update("UPDATE ActionProperty_Action SET value=? where id=? and `index`=?", actKeyHolder.getKey().intValue(), apQueueId, apQueueIdx);

            resId = actKeyHolder.getKey().intValue();

            try {
                wtService.logClosePARequest(idPar,actKeyHolder.getKey().intValue());
            } catch (Exception ex) {}
        } finally {
            appService.releaseActionLock(vLockId);
        }
        return resId;
    }

    private Boolean checkPatient(String patId, String surname, Date birthDate) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM Client where id=? and UPPER(lastname)=? and birthDate=?",new Object[]{patId,surname.toUpperCase(),birthDate},Integer.class)==1;
    }

    public List<Lpu> getRegLpus() {
        return misConfig.getLpus();
    }

    public List<SpecTuple> getRegSpec(Integer lpuId) {
        MapSqlParameterSource param = queueService.getCommonSQLParameters(lpuId);
        return jdbcTemplateNP.query("select distinct s.id, s.code, s.name from Person p " +
                "join rbSpeciality s on s.id=p.speciality_id " +
                "where p.orgStructure_id in (:orgs) AND p.retireDate is null AND p.availableForExternal=1 " +
                "order by s.name", param, (rs, i) ->  new SpecTuple(rs.getString("id"), rs.getString("code"),rs.getString("name")) );
    }

    public List<DoctorRes> getRegDoct(Integer lpuId, Integer specId) {
        MapSqlParameterSource param = queueService.getCommonSQLParameters(lpuId);
        param.addValue("specId", specId);
        return jdbcTemplateNP.query("select p.id, CONCAT(p.lastName,' ',p.firstName,' ',p.patrName) as name from Person p " +
                "where p.orgStructure_id in (:orgs) AND p.retireDate is null AND p.availableForExternal=1 AND p.speciality_id=:specId " +
                "order by p.lastName,p.firstName, p.patrName", param, (rs, i) ->  {
            DoctorRes doct = new DoctorRes();
            doct.setName(rs.getString("name"));
            doct.setId(rs.getInt("id"));
            return doct;
        });
    }

    public String getPatientById(Integer patId) {
        return jdbcTemplate.queryForObject("SELECT CONCAT(lastName,' ',firstName,' ',patrName,' ',DATE(birthDate)) FROM Client where id=?", new Object[]{patId}, String.class);
    }

    public String getPatientPhone(Integer patId) {
        String phone;
        try {
            phone = jdbcTemplate.queryForObject("select contact from ClientContact cc where cc.deleted=0 and cc.client_id=? order by cc.isPrimary desc, length(cc.contact) desc, cc.id desc limit 1", new Object[]{patId}, String.class);
        } catch (IncorrectResultSizeDataAccessException ex) {
            return "";
        }
        phone = phone.replace(" ","").replace("-","").replace("+","");
        if (!phone.matches("\\d+")) return "";
        if (phone.length()==7) return "8(812)"+phone;
        if (phone.length()==10) return "8("+phone.substring(0,3)+")"+phone.substring(3,9);
        if (phone.length()==11) return "8("+phone.substring(1,4)+")"+phone.substring(4,11);
        return "";
    }

    public String registerPA(Integer patId,Integer lpuId,Integer specId,Integer doctId,Integer reason, String info, String phone) {
        RegisterPARequestRequest req = new RegisterPARequestRequest();
        RegisterPARequest r = new RegisterPARequest();
        r.setRequest(req);
        r.setGuid(misConfig.getGuid());

        Map<String, Object> specMap = jdbcTemplate.queryForMap("SELECT id, code, name from rbSpeciality where id=?", specId);

        PARequest par = new PARequest();
        par.setIdLpu(misConfig.getLpuMap().get(lpuId).getRemoteId().toString());
        par.setFerIdSpeciality(specMap.get("code").toString());
        par.setIdSpeciality(specMap.get("id").toString());
        par.setNameSpeciality(specMap.get("name").toString());
        if (doctId!=null && doctId!=0) {
            par.setNameDoc(jdbcTemplate.queryForObject("SELECT CONCAT(lastName,' ',firstName,' ',patrName) from Person where id=?", new Object[]{doctId}, String.class));
            par.setIdDoc(doctId.toString());
        }
        req.setPARequest(par);

        PARequestPatient pat = new PARequestPatient();
        Map<String, Object> patMap = jdbcTemplate.queryForMap("SELECT lastName,firstName,patrName,birthDate  from Client where id=?", patId);
        pat.setIdPatient(patId.toString());
        pat.setLastName(patMap.get("lastName").toString());
        pat.setFirstName(patMap.get("firstName").toString());
        pat.setMiddleName(patMap.get("patrName").toString());
        pat.setBirthDate(Utils.toXMLDate((Date) patMap.get("birthDate")));

        PARequestPatientContacts pac = new PARequestPatientContacts();
        pac.setPhone(phone);
        pat.setPARequestPatientContacts(pac);

        req.setPARequestPatient(pat);

        req.setPASource(1);

        PARequestInfo pai = new PARequestInfo();
        pai.setClaim(reason.toString());
        pai.setInfo(info);
        req.setPARequestInfo(pai);

        try {
            RegisterPARequestResponse resp = (RegisterPARequestResponse) soapConnector.callWebService(misConfig.getWebService(), "RegisterPARequest", r);
            if (resp.getRegisterPARequestResult().isSuccess()) {
                return "Зарегистрирован в ЖОС №"+resp.getRegisterPARequestResult().getIdPar();
            } else {
                return "Ошибка! "+ resp.getRegisterPARequestResult().getErrorList().getError().get(0).getErrorDescription();
            }
        } catch (Exception ex) {
            return "Ошибка! " + ex.toString();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void logRegisterPARequest(PARequestRes par) {
        try {
            Integer stat = jdbcTemplate.queryForObject("SELECT status from p106.PALog where idPar=?", new Object[]{par.getIdPar()}, Integer.class);
            par.setStatus(stat);
            return;
        } catch (DataAccessException ex) {}


        String query = "INSERT INTO p106.PALog (`idLpu`, `lpuName`, `idPar`, `createDatetime`, `idSpec`, `idDoc`, `claim`, `source`, `status`, `idPat`, `surname`, `name`, `secondName`, `birthDate`, `phone`) " +
                "VALUES (:idLpu, :lpuName, :idPar, :createDatetime, :idSpec, :idDoc, :claim, :source, :status,  :idPat, :surname, :name, :secondName, :birthDate, :phone)";

        MapSqlParameterSource actParam = new MapSqlParameterSource();
        actParam.addValue("idLpu", par.getIdLpu());
        actParam.addValue("lpuName", par.getLpuName());
        actParam.addValue("idPar", par.getIdPar());
        actParam.addValue("createDatetime", par.getCreated());
        actParam.addValue("idSpec", par.getIdSpec());
        actParam.addValue("idDoc", par.getIdDoc());
        actParam.addValue("claim", par.getClaim());
        actParam.addValue("source", par.getSource());
        actParam.addValue("status", par.getStatus());
        actParam.addValue("idPat", par.getPatient().getIdPat());
        actParam.addValue("surname", par.getPatient().getSurname());
        actParam.addValue("name", par.getPatient().getName());
        actParam.addValue("secondName", par.getPatient().getSecondName());
        actParam.addValue("birthDate", par.getPatient().getBirthDate());
        actParam.addValue("phone", par.getPatient().getPhone());
        
        jdbcTemplateNP.update(query,actParam);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void logClosePARequest(String par, Integer appId) {
        jdbcTemplate.update("UPDATE p106.PALog set appointmentId=?, status=2 where idPar=?",new Object[]{appId, par});
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void logRemovePARequest(String par, Integer deactivateReason, String deactivateComment) {
        jdbcTemplate.update("UPDATE p106.PALog set deactivateDate=NOW(),deactivateReason=?, deactivateComment=?, status=3 where idPar=?",new Object[]{deactivateReason, deactivateComment, par});
    }

}
