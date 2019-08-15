package ru.tdi.misintegration.szpv.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.tdi.misintegration.szpv.config.SoapConnector;
import ru.tdi.misintegration.szpv.ws.*;
import ru.tdi.misintegration.szpv.Utils;
import ru.tdi.misintegration.szpv.config.MisConfig;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AppointmentService {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplateNP;

    @Autowired
    MisConfig misConfig;

    @Autowired
    AppointmentService appService;

    @Autowired
    UserService userService;

    @Autowired
    SoapConnector soapConnector;

    @Autowired
    PatientService ptSetvice;

    org.slf4j.Logger log = LoggerFactory.getLogger(this.getClass());

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createQueueRecords(Integer actionId, boolean needLock) {
        Integer qMax = jdbcTemplate.queryForObject("select max(apq.index)  from Action " +
                "JOIN ActionProperty ap2 on ap2.action_id=Action.id and ap2.type_id=? " +
                "JOIN ActionProperty_Action apq ON apq.id = ap2.id  " +
                "where Action.id = ? ", new Object[]{misConfig.getQAPType(), actionId}, Integer.class);
        if (qMax==null) qMax=-1;
        Integer tMax = jdbcTemplate.queryForObject("select max(apq.index)  from Action " +
                "JOIN ActionProperty ap2 on ap2.action_id=Action.id and ap2.type_id=? " +
                "JOIN ActionProperty_Time apq ON apq.id = ap2.id " +
                "where Action.id = ? ", new Object[]{misConfig.getTAPType(), actionId}, Integer.class);

        if (tMax!=null && qMax < tMax) {
            Integer vLockId = null;
            if (needLock) {
                vLockId = appService.getActionLock(actionId);

                if (vLockId == -1) {
                    throw new RFSZException(39, "Талон к врачу занят/заблокирован");
                }
            }

            try {
                MapSqlParameterSource param = new MapSqlParameterSource();
                param.addValue("actionId", actionId);
                param.addValue("actionPQueueId", misConfig.getQAPType());

                List<Integer> apIds = jdbcTemplateNP.queryForList("SELECT id from ActionProperty where action_id=:actionId and type_id=:actionPQueueId and deleted=0", param, Integer.class);
                Integer apId;
                if (apIds.isEmpty()) {
                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    jdbcTemplateNP.update("INSERT INTO ActionProperty(createDatetime,createPerson_id,modifyDatetime,norm,action_id,type_id) VALUES (NOW(),NULL,NOW(),'',:actionId,:actionPQueueId)", param, keyHolder);
                    apId = keyHolder.getKey().intValue();
                } else {
                    apId = apIds.get(0);
                }

                for (int i = qMax + 1; i <= tMax; i++) {
                    jdbcTemplate.update("INSERT INTO ActionProperty_Action(id, `index`, value) VALUES (?,?,NULL)", apId, i);
                }
            } finally {
                if (vLockId!=null) appService.releaseActionLock(vLockId);
            }
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SetAppointmentResult setAppointment(SetAppointment app) {
        SetAppointmentResult res = new SetAppointmentResult();

        if (app.getAttachedReferral() != null && app.getIdPat() == null) {
            app.setIdPat(ptSetvice.checkPatient(app.getAttachedReferral().getReferral().getPatient().getPerson()));
        }

        String[] appD = app.getIdAppointment().split("_");
        Integer doctId = new Integer(appD[0]);
        String appTime = appD[2];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date appDate;
        try {
            appDate = sdf.parse(appD[1]);
        } catch (ParseException e) {
            res.setSuccess(false);
            res.setErrorList(Utils.setError(99, "Неверный талон"));
            return res;
        }

        Integer elig = jdbcTemplate.queryForObject("select isClientEligibleApp(?,?);", new Object[]{app.getIdPat(), doctId}, Integer.class);
        if (elig != 1) {
            res.setSuccess(false);
            switch (elig) {
                case 36:
                    res.setErrorList(Utils.setError(36, "Пациент не имеет прикрепления к данному врачебному участку"));
                    break;
                case 301:
                    res.setErrorList(Utils.setError(34, "Запись запрещена (только до 1 года)"));
                    break;
                case 302:
                    res.setErrorList(Utils.setError(34, "Запись запрещена (только с 1 года)"));
                    break;
                case 303:
                    res.setErrorList(Utils.setError(33, "Врач не соответствует профилю пациента"));
                    break;
                case 304:
                    res.setErrorList(Utils.setError(22, "Несоответствие записи акта гражданского состояния"));
                    break;
                default:
                    res.setErrorList(Utils.setError(99, "Неверно указаны параметры"));
                    break;
            }
            return res;
        }

        if (hasPrevoiusApps(app.getIdPat(), doctId, appDate)) {
            res.setSuccess(false);
            res.setErrorList(Utils.setError(35, "Пациент имеет предстоящую запись к данному врачу/врачу этой специальности"));
            return res;
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("eventTypeId", misConfig.getQEventType());
        parameters.addValue("actionPTimesId", misConfig.getTAPType());
        parameters.addValue("actionPQueueId", misConfig.getQAPType());
        parameters.addValue("actionTypeId", misConfig.getQActionType());
        parameters.addValue("doctId", doctId);
        parameters.addValue("appDate", appDate);
        parameters.addValue("appTime", appTime);

        String queryActionId = "select Action.id from Action " +
                "LEFT JOIN Event ON Event.id = Action.event_id " +
                "LEFT JOIN EventType ON EventType.id = Event.eventType_id " +
                "LEFT JOIN Person ON Person.id = Event.setPerson_id " +
                "where " +
                "Event.deleted=0 AND Action.deleted=0 AND  Event.eventType_id=:eventTypeId " +
                "AND Action.actionType_id=:actionTypeId " +
                "AND Event.setPerson_id = :doctId AND Event.setDate = :appDate " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate) " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate),DATETIME),'08:00')<=ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))";

        Integer actionId = jdbcTemplateNP.queryForObject(queryActionId, parameters, Integer.class);

        Integer vLockId = appService.getActionLock(actionId);

        if (vLockId == -1) {
            res.setSuccess(false);
            res.setErrorList(Utils.setError(39, "Талон к врачу занят/заблокирован"));
            return res;
        }

        try {

            String queryApp = "select Action.id, apt.index as tIdx, apt.value as tVal, apq.id as qId, apq.index as qIdx, apq.value as qVal  from Action " +
                    "LEFT JOIN ActionProperty ap on ap.action_id=Action.id " +
                    "LEFT JOIN ActionProperty_Time apt ON apt.id = ap.id and ap.type_id=:actionPTimesId " +
                    "LEFT JOIN ActionProperty ap2 on ap2.action_id=Action.id and ap2.type_id=:actionPQueueId  " +
                    "LEFT JOIN ActionProperty_Action apq ON apq.id = ap2.id and apq.index=apt.index " +
                    "where " +
                    "Action.id=:actionId AND apt.value = TIME(:appTime) ";

            parameters.addValue("actionId", actionId);

            List<Map<String, Object>> queue = jdbcTemplateNP.queryForList(queryApp, parameters);

            if (queue.isEmpty() || queue.size() > 1) {
                res.setSuccess(false);
                res.setErrorList(Utils.setError(38, "Указан недопустимый идентификатор талона на запись"));
                return res;
            }

            // Запись с нужным индексом в свойстве queue отсутствует
            if (queue.get(0).get("qId")==null) {
                appService.createQueueRecords(actionId, false);
                queue = jdbcTemplateNP.queryForList(queryApp, parameters);
                if (queue.isEmpty() || queue.size() > 1) {
                    res.setSuccess(false);
                    res.setErrorList(Utils.setError(38, "Указан недопустимый идентификатор талона на запись"));
                    return res;
                }
            }

            if (queue.get(0).get("qVal") != null) {
                res.setSuccess(false);
                res.setErrorList(Utils.setError(39, "Талон к врачу занят/заблокирован"));
                return res;
            }

            Integer apQueueId = (Integer) queue.get(0).get("qId");
            Integer apQueueIdx = (Integer) queue.get(0).get("qIdx");

            if (actionId == null || apQueueId == null || apQueueIdx == null) {
                res.setSuccess(false);
                res.setErrorList(Utils.setError(38, "Указан недопустимый идентификатор талона на запись"));
                return res;
            }

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
            evtParam.addValue("clientId", app.getIdPat());
            evtParam.addValue("setDate", appDate);
            evtParam.addValue("setTime", appTime);
            evtParam.addValue("userId", misConfig.getUserId());
            KeyHolder evtKeyHolder = new GeneratedKeyHolder();
            jdbcTemplateNP.update("INSERT INTO Event(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id,  externalId, `order`, payStatus, note, totalCost, eventType_id, org_id, client_id, setDate, isPrimary) " +
                    "VALUES (NOW(),:userId, NOW(),:userId, '',1,0,'',0,:eventType,:orgId,:clientId,ADDTIME(CONVERT(DATE(:setDate),DATETIME),:setTime),1); ", evtParam, evtKeyHolder);

            MapSqlParameterSource actParam = new MapSqlParameterSource();
            actParam.addValue("actionType", misConfig.getAppActionType());
            actParam.addValue("eventId", evtKeyHolder.getKey().intValue());
            actParam.addValue("personId", doctId);
            actParam.addValue("setDate", appDate);
            actParam.addValue("setTime", appTime);
            actParam.addValue("office", office);
            actParam.addValue("userId", misConfig.getUserId());

            String note = "ЕГИСЗ: " + userService.getUsername(app.getGuid());
            if (app.getDoctorsReferral() != null && !app.getDoctorsReferral().isEmpty() && app.getAttachedReferral() == null)
                note = note + " ЖОС УО№" + app.getDoctorsReferral();
            if (app.getDoctorsReferral() != null && !app.getDoctorsReferral().isEmpty() && app.getAttachedReferral() != null)
                note = note + " Напр. УО№" + app.getDoctorsReferral();
            note = note + " УИВ:" + app.getGuid();
            actParam.addValue("note", note);
            KeyHolder actKeyHolder = new GeneratedKeyHolder();
            jdbcTemplateNP.update("INSERT INTO Action(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, plannedEndDate, amount, account, payStatus, MKB, morphologyMKB,coordText, " +
                    "actionType_id, event_id, directionDate, status, setPerson_id, note, person_id, office) " +
                    "VALUES (NOW(),:userId, NOW(),:userId, '0000-00-00 00:00', 0, 0, 0, '','','', :actionType,:eventId, ADDTIME(CONVERT(DATE(:setDate),DATETIME),:setTime), 1, NULL, :note, :personId, :office);", actParam, actKeyHolder);


            jdbcTemplate.update("UPDATE ActionProperty_Action SET value=? where id=? and `index`=?", actKeyHolder.getKey().intValue(), apQueueId, apQueueIdx);
        } finally {
            appService.releaseActionLock(vLockId);
        }
        res.setSuccess(true);
        res.setType(SpecialistType.ОТСУТСТСТВУЕТ_НЕОПРЕДЕЛЕНО);
        return res;
    }

    public SetAppointmentByPARequestResult setAppointmentByPARequest(SetAppointmentByPARequest app) {
        SetAppointment nApp = new SetAppointment();
        nApp.setGuid(app.getGuid());
        nApp.setIdAppointment(app.getIdAppointment());
        nApp.setIdAppointmentPrev(app.getIdAppointmentPrev());
        nApp.setIdLpu(app.getIdLpu());
        nApp.setIdHistory(app.getIdHistory());
        nApp.setIdPat(app.getAttachedPARequest().getPARequestPatient().getIdPatient());
        nApp.setDoctorsReferral(app.getAttachedPARequest().getIdPar());

        SetAppointmentResult nRes = appService.setAppointment(nApp);
        SetAppointmentByPARequestResult res = new SetAppointmentByPARequestResult();
        res.setType(1);
        res.setSuccess(nRes.isSuccess());
        res.setErrorList(nRes.getErrorList());
        res.setIdHistory(nRes.getIdHistory());

        return res;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateClaimForRefusalResult createClaimForRefusal(String idPat, String idApp) {
        CreateClaimForRefusalResult res = new CreateClaimForRefusalResult();

        String query = "select apQ.id, apQ.`index` " +
                "from ActionProperty_Action apQ, Action act " +
                "join Event evt on evt.id = act.event_id " +
                "where apQ.value=:idQueue and act.id=:idQueue and act.actionType_id=:qActionTypeId and evt.client_id=:clientId and act.deleted=0";

        MapSqlParameterSource qParam = new MapSqlParameterSource();
        qParam.addValue("idQueue", idApp);
        qParam.addValue("clientId", idPat);
        qParam.addValue("qActionTypeId", misConfig.getAppActionType());

        List<Map<String, Object>> queue = jdbcTemplateNP.queryForList(query, qParam);

        if (queue.isEmpty() || queue.size() > 1) {
            res.setErrorList(Utils.setError(75, "Талон с указанным номером не существует или уже отменен"));
            res.setSuccess(false);
            return res;
        }

        jdbcTemplate.update("UPDATE ActionProperty_Action set value=NULL where id=? and `index`=?", queue.get(0).get("id"), queue.get(0).get("index"));
        jdbcTemplate.update("UPDATE Action set deleted=1 where id=?", idApp);

        res.setSuccess(true);

        return res;
    }

    private boolean hasPrevoiusApps(String clientId, Integer doctId, Date dt) {
        String query = "select count(QueueAction.id) as cnt from Action AS QueueAction " +
                "            LEFT JOIN ActionType AS QueueActionType ON QueueActionType.id = QueueAction.actionType_id " +
                "            LEFT JOIN Person     AS QueuePerson     ON QueuePerson.id = QueueAction.person_id " +
                "            LEFT JOIN Event      AS QueueEvent      ON QueueEvent.id = QueueAction.event_id " +
                "            LEFT JOIN EventType  AS QueueEventType  ON QueueEventType.id = QueueEvent.eventType_id " +
                "            LEFT JOIN ActionProperty_Action         ON ActionProperty_Action.value = QueueAction.id " +
                "            LEFT JOIN ActionProperty                ON ActionProperty.id = ActionProperty_Action.id " +
                "            LEFT JOIN Action                        ON Action.id = ActionProperty.action_id " +
                "            LEFT JOIN ActionType                    ON ActionType.id = Action.actionType_id " +
                "            LEFT JOIN Event                         ON Event.id = Action.event_id " +
                "            LEFT JOIN EventType                     ON EventType.id = Event.eventType_id " +
                "            LEFT JOIN Person                        ON Person.speciality_id = QueuePerson.speciality_id " +
                "          where " +
                "            QueueAction.deleted = 0 " +
                "            AND QueueActionType.code = 'queue' " +
                "            AND QueueEvent.deleted = 0 " +
                "            AND QueueEventType.code = 'queue' " +
                "            AND Action.deleted = 0 " +
                "            AND ActionType.code = 'amb' " +
                "            AND Event.deleted = 0 " +
                "            AND EventType.code = '0' " +
                "            AND QueueEvent.client_id = ? " +
                "            AND Person.id = ? " +
                "            AND QueueEvent.setDate > NOW()";

        return jdbcTemplate.queryForObject(query, new Object[]{clientId, doctId}, Integer.class) > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_UNCOMMITTED)
    public Integer getActionLock(Integer id) {
        return jdbcTemplate.queryForObject("SELECT getSimpleAppLock('Action',?);", new Object[]{id}, Integer.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_UNCOMMITTED)
    public void releaseActionLock(Integer id) {
        jdbcTemplate.execute("CALL ReleaseSimpleAppLock(" + id.toString() + ")");
    }

    public void sendAppointmentNotification(Integer queueId) {
        String condSql = "AND NeedAction.id=:queueId";
        MapSqlParameterSource qParamAdd = new MapSqlParameterSource();
        qParamAdd.addValue("queueId", queueId);

        sendAppointmentNotification(condSql, qParamAdd, true);
    }

    public void sendAppointmentNotification(Date dtBegin, Date dtEnd) {
        String condSql = "AND (DATE(NeedAction.createDatetime) >= DATE(:dtBegin)) AND (DATE(NeedAction.createDatetime) <= DATE(:dtEnd))";
        MapSqlParameterSource qParamAdd = new MapSqlParameterSource();
        qParamAdd.addValue("dtBegin", dtBegin);
        qParamAdd.addValue("dtEnd", dtEnd);

        sendAppointmentNotification(condSql, qParamAdd, false);
    }


    private void sendAppointmentNotification(String condSql, MapSqlParameterSource qParamAdd, boolean immediate) {

        String query = "SELECT "
                + "	 NeedAction.id as actionId, NeedAction.createDatetime as dtCreate, NeedAction.modifyDatetime as dtChange, " +
                "cl.id as clientId, cl.lastName, cl.firstName, cl.patrName, cl.birthDate, " +
                "spec.id as specId, spec.name as specName, spec.code as specFerId,  " +
                "ExecPerson.id as doctId, CONCAT(ExecPerson.lastName,' ',ExecPerson.firstName,' ',ExecPerson.patrName) as doctName, ExecPerson.SNILS, " +
                "NeedAction.directionDate as dt, " +
                "CONCAT(DATE(NeedAction.directionDate),'T',TIME(NeedAction.directionDate)) AS dtStr, " +
                "CONCAT(DATE(NeedAction.createDatetime),'T',TIME(NeedAction.createDatetime)) AS dtCreateStr, "
                + "	 case "
                + "	 when (NeedAction.setPerson_id is not NULL and NeedAction.setPerson_id<>:ctoId and Person.post_id NOT IN (4,208)) THEN 'Врач'  "
                + "    when (NeedAction.setPerson_id is not NULL and NeedAction.setPerson_id<>:ctoId and Person.post_id IN (4,208)) THEN 'Регистратура'                      "
                + "    when (NeedAction.setPerson_id=:ctoId OR (NeedAction.setPerson_id is NULL and (INSTR(NeedAction.note,'Call')>0))) THEN 'Колл-центр' "
                + "    when (NeedAction.setPerson_id is NULL and INSTR(NeedAction.note,'П-ИНФОМАТ')>0) THEN 'Терминал' "
                + "    when (NeedAction.setPerson_id is NULL and INSTR(NeedAction.note,'ЕГИСЗ')>0) THEN 'СЗПВ' END  as WHO, "
                + "	 toporg.orgId AS DEPT, "
                + "    NeedAction.deleted, "
                + "    NeedAction.note "
                + "    FROM Action NeedAction "
                + "    LEFT JOIN Event ON Event.id = NeedAction.event_id "
                + "    LEFT JOIN Client cl ON cl.id = Event.client_id    "
                + "    LEFT JOIN Person ON Person.id = NeedAction.setPerson_id "
                + "    LEFT JOIN Person AS ExecPerson ON ExecPerson.id = NeedAction.person_id "
                + "	 left join p106.TopOrgs toporg on toporg.id=ExecPerson.orgStructure_id "
                + "	 LEFT JOIN rbSpeciality spec ON ExecPerson.speciality_id = spec.id "
                + "	 left join p106.EGISZLog log on (log.reqType='AppNotify' and log.objType='action' and log.objId=NeedAction.id and log.success=1) "
                + "    WHERE "
                + "    log.id is NULL AND ExecPerson.post_id<>63 AND NeedAction.actionType_id=19 " + condSql;

        MapSqlParameterSource qParam = new MapSqlParameterSource();
        qParam.addValue("ctoId", misConfig.getCtoUserId());
        qParam.addValues(qParamAdd.getValues());

        jdbcTemplateNP.query(query, qParam, (rs, i) -> {
            SendNotificationAboutAppointment notify = new SendNotificationAboutAppointment();
            try {
                String who = rs.getString("WHO");
                String note = rs.getString("note");

                if (who == null) who = "Регистратура";
                if (who.equals("Врач")) notify.setAppointmentSource(AppointmentSourceType.ВРАЧ_АПУ);
                if (who.equals("Регистратура")) notify.setAppointmentSource(AppointmentSourceType.РЕГИСТРАТУРА);
                if (who.equals("Колл-центр")) notify.setAppointmentSource(AppointmentSourceType.ЦТО);
                if (who.equals("Терминал")) notify.setAppointmentSource(AppointmentSourceType.ИНФОМАТ);
                if (who.equals("Интернет")) notify.setAppointmentSource(AppointmentSourceType.ИНТЕРНЕТ);
                if (who.equals("СЗПВ")) {
                    notify.setAppointmentSource(AppointmentSourceType.ПРОЧЕЕ);
                    Pattern p = Pattern.compile("УИВ:(.*)($|\\s)");
                    Matcher m = p.matcher(note);
                    if (m.find()) {
                        notify.setMember(m.group(1));
                    } else {
                        throw new RFSZException(0,"No member GUID present.");
                    }
                }

                notify.setIdLpu(misConfig.getTopOrgMap().get(rs.getInt("DEPT")).getRemoteId());
                notify.setGuid(misConfig.getGuid());

                if (note != null) {
                    Pattern p = Pattern.compile("УО№(\\d+)");
                    Matcher m = p.matcher(note);
                    if (m.find()) notify.setDoctorsReferal(m.group(1));
                }

                Doctor doc = new Doctor();
                doc.setIdDoc(rs.getString("doctId"));
                doc.setName(rs.getString("doctName"));
                doc.setSnils(Utils.formatSNILS(rs.getString("SNILS")));
                notify.setDoctor(doc);

                Spesiality spec = new Spesiality();
                spec.setFerIdSpesiality(rs.getString("specFerId"));
                spec.setIdSpesiality(rs.getString("specId"));
                spec.setNameSpesiality(rs.getString("specName"));
                notify.setSpesiality(spec);

                Patient pat = new Patient();
                pat.setSurname(rs.getString("lastName"));
                pat.setName(rs.getString("firstName"));
                pat.setSecondName(rs.getString("patrName"));
                pat.setBirthday(Utils.toXMLDate(rs.getDate("birthDate")));
                pat.setIdPat(rs.getString("clientId"));
                notify.setPatient(pat);

                Appointment app;
                if (immediate) {
                    app = new Appointment();
                } else {
                    NoticeAppointment appN = new NoticeAppointment();
                    appN.setEventDateTime(Utils.toXMLDateTime(rs.getString("dtCreateStr")));
                    app = appN;
                }
                app.setVisitStart(Utils.toXMLDateTime(rs.getString("dtStr")));
                app.setVisitEnd(Utils.toXMLDateTime(rs.getString("dtStr")));
                app.setIdAppointment(rs.getString("actionId"));
                notify.setAppointment(app);

                SendNotificationAboutAppointmentResponse resp = (SendNotificationAboutAppointmentResponse) soapConnector.callWebService(misConfig.getWebService(), "SendNotificationAboutAppointment", notify);

                ObjectMapper mapper = new ObjectMapper();
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.setTimeZone(TimeZone.getDefault());

                jdbcTemplate.update("INSERT INTO p106.EGISZLog(reqType,objType,objId,success,request,response,remoteId) VALUES('AppNotify','action',?,?,?,?,?);",
                        rs.getString("actionId"),
                        resp.getSendNotificationAboutAppointmentResult().isSuccess() ? 1 : 0,
                        mapper.writeValueAsString(notify),
                        mapper.writeValueAsString(resp),
                        resp.getSendNotificationAboutAppointmentResult().getIdNotification() == null ? "" : resp.getSendNotificationAboutAppointmentResult().getIdNotification().toString());
            } catch (JsonProcessingException | DataAccessException | RFSZException ex) {
                log.error("SendNotify", ex);
            }
            return notify;
        });
    }

    public void sendCancelNotifications(Date dtBegin, Date dtEnd) {
        String query = "select NeedAction.id, CONCAT(DATE(NeedAction.modifyDatetime),'T',TIME(NeedAction.modifyDatetime)) AS dtStr, NeedAction.modifyDatetime, logN.remoteId  from Action NeedAction " +
                "join p106.EGISZLog logN on (logN.reqType='AppNotify' and logN.objType='action' and logN.objId=NeedAction.id and logN.success=1 and logN.remoteId IS NOT NULL and logN.remoteId<>'') " +
                "left join p106.EGISZLog log on (log.reqType='AppCancel' and log.objType='action' and log.objId=NeedAction.id and log.success=1) " +
                "where " +
                "NeedAction.deleted=1 AND log.id is NULL AND NeedAction.actionType_id=19 " +
                "AND (DATE(NeedAction.modifyDatetime) >= DATE(:dtBegin)) AND (DATE(NeedAction.modifyDatetime) <= DATE(:dtEnd))";

        MapSqlParameterSource qParam = new MapSqlParameterSource();
        qParam.addValue("dtBegin", dtBegin);
        qParam.addValue("dtEnd", dtEnd);

        jdbcTemplateNP.query(query, qParam, (rs, i) -> {
            return sendStatusNotification(rs.getInt("id"),rs.getLong("remoteId"),rs.getString("dtStr"),AppointmentStatusType.ЗАПИСЬ_ОТМЕНЕНА_ПО_ИНИЦИАТИВЕ_ПАЦИЕНТА, "AppCancel" );
        });
    }

    public void sendDoneNotifications(Date dtBegin, Date dtEnd) {
        String query = "select NeedAction.id, CONCAT(DATE(v.createDatetime),'T',TIME(v.createDatetime)) AS dtStr, v.createDatetime, logN.remoteId  from Action NeedAction " +
                "join p106.EGISZLog logN on (logN.reqType='AppNotify' and logN.objType='action' and logN.objId=NeedAction.id and logN.success=1 and logN.remoteId IS NOT NULL and logN.remoteId<>'') " +
                "left join p106.EGISZLog log on (log.reqType='AppDone' and log.objType='action' and log.objId=NeedAction.id and log.success=1) " +
                "join Event NeedEvent on NeedAction.event_id=NeedEvent.id " +
                "join Event evt on (evt.client_id=NeedEvent.client_id and evt.deleted=0) " +
                "join Visit v on (v.deleted=0 and v.event_id=evt.id and v.person_id=NeedAction.person_id and v.date=DATE(NeedAction.directionDate)) " +
                "where " +
                "NeedAction.deleted=0 AND log.id is NULL AND NeedAction.actionType_id=19 " +
                "AND (DATE(NeedAction.directionDate) >= DATE(:dtBegin)) AND (DATE(NeedAction.directionDate) <= DATE(:dtEnd))";

        MapSqlParameterSource qParam = new MapSqlParameterSource();
        qParam.addValue("dtBegin", dtBegin);
        qParam.addValue("dtEnd", dtEnd);

        jdbcTemplateNP.query(query, qParam, (rs, i) -> {
            return sendStatusNotification(rs.getInt("id"),rs.getLong("remoteId"),rs.getString("dtStr"),AppointmentStatusType.ПОСЕЩЕНИЕ_СОСТОЯЛОСЬ, "AppDone" );
        });
    }

    private SendNotificationAboutAppointmentStatus sendStatusNotification(Integer actionId, Long idNotification, String dt, AppointmentStatusType stat, String reqType) {
        SendNotificationAboutAppointmentStatus status = new SendNotificationAboutAppointmentStatus();
        try {
            status.setGuid(misConfig.getGuid());
            status.setIdNotification(idNotification);
            status.setEventDatetime(Utils.toXMLDateTime(dt));
            status.setStatus(stat);

            SendNotificationAboutAppointmentStatusResponse resp = (SendNotificationAboutAppointmentStatusResponse) soapConnector.callWebService(misConfig.getWebService(), "SendNotificationAboutAppointmentStatus", status);

            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.setTimeZone(TimeZone.getDefault());

            jdbcTemplate.update("INSERT INTO p106.EGISZLog(reqType,objType,objId,success,request,response,remoteId) VALUES(?,'action',?,?,?,?,NULL);",
                    reqType,
                    actionId,
                    resp.getSendNotificationAboutAppointmentStatusResult().isSuccess() ? 1 : 0,
                    mapper.writeValueAsString(status),
                    mapper.writeValueAsString(resp));
        } catch (JsonProcessingException | DataAccessException ex) {
            log.error("SendCancel", ex);
        }
        return status;
    }

}
