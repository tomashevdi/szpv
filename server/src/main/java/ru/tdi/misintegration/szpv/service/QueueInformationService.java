package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import ru.tdi.misintegration.szpv.config.Lpu;
import ru.tdi.misintegration.szpv.model.AbsenceReason;
import ru.tdi.misintegration.szpv.model.Doctor2Tuple;
import ru.tdi.misintegration.szpv.model.Doctor3WithSpec;
import ru.tdi.misintegration.szpv.config.MisConfig;
import ru.tdi.misintegration.szpv.model.SpecTuple;
import ru.tdi.misintegration.szpv.Utils;
import ru.tdi.misintegration.szpv.ws.*;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Service
public class QueueInformationService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplateNP;

    @Autowired
    MisConfig misConfig;

    @Autowired
    PatientService ptService;

    @Autowired
    QueueInformationService queueService;

    @Cacheable(cacheNames = "orgStructs")
    public List<Integer> getOrgStructs(Integer orgId) {
        List<Integer> res = findOrgStructs(orgId);
        res.add(orgId);
        return res;
    }

    private List<Integer> findOrgStructs(Integer orgId) {
        List<Integer> orgS = jdbcTemplate.query("select id from OrgStructure o where o.deleted = 0 and o.availableForExternal = 1 and o.parent_id = ?;",
                new Object[]{orgId}, (resultSet, i) -> resultSet.getInt("id"));
        List<Integer> res = new ArrayList<>();
        for (Integer id : orgS) {
            res.addAll(findOrgStructs(id));
        }
        res.addAll(orgS);
        return res;
    }

    @Cacheable(cacheNames = "specList")
    public List<Spesiality> getSpecialityList(Integer lpuId) {
        String query = "select specId, s.code as ferCode, s.OKSOName as specName, sum(total) as total, sum(free) as free, sum(freeI) as available, sum(totalI) as sTotalI, min(dateI) as minDate, max(dateI) as maxDate from ( " +
                "select p.speciality_id as specId, doctId, date, sum(queueI) as qExt, sum(free) as free, sum(total) as total, q.value, IF(floor((sum(total)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(total)*q.value)/100)-sum(queueI),sum(free)),0)  as freeI, floor((sum(total)*q.value)/100) as totalI," +
                "IF(IF(floor((sum(total)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(total)*q.value)/100)-sum(queueI),sum(free)),0)>0,date, null) as dateI from ( " +
                "select  " +
                "Event.setPerson_id as doctId,  DATE(Event.setDate) as date, 1 as total, IF(atQueue.value IS NULL,1,0) as free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) as queueI  " +
                "from Action " +
                "left join ActionType ON ActionType.id = Action.actionType_id " +
                "left join Event ON (Event.id = Action.event_id and Event.eventType_id=:eventTypeId) " +
                "left join Person ON Person.id = Event.setPerson_id " +
                "left join Action aT ON aT.id=Action.id " +
                "left join ActionProperty atP on (atP.action_id=aT.id and atP.type_id=:actionPTimesId)   " +
                "left join ActionProperty_Time atTime on atTime.id=atP.id  " +
                "left join ActionProperty aqP on (aqP.action_id=aT.id and aqP.type_id=:actionPQueueId)   " +
                "left join ActionProperty_Action atQueue on (atQueue.id=aqP.id and atQueue.`index`=atTime.`index`) " +
                "left join Action qA on qA.id=atQueue.value  " +
                "where  " +
                "Event.deleted=0  " +
                "AND Action.deleted=0  " +
                "AND atTime.value is not null  " +
                "AND Action.actionType_id=:actionTypeId  " +
                "AND Event.setPerson_id IN (SELECT id from Person p where p.availableForExternal=1 and p.orgStructure_id in (:orgs) and p.retireDate is null) " +
                "AND Event.setDate >= :dtBegin  " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate) " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate),DATETIME),'08:00')<=ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 and atP.id is not null  " +
                "AND (aqP.deleted=0 or aqP.id is null)  " +
                "AND atTime.value IS NOT NULL " +
                "UNION ALL " +
                "SELECT p.id as doctId, null, 0,0,0 from Person p where p.availableForExternal=1 and p.orgStructure_id in (:orgs) and p.retireDate is null  " +
                ") as T  " +
                "left join PersonPrerecordQuota q on q.person_id=doctId " +
                "left join rbPrerecordQuotaType qT on qT.id=q.quotaType_id  " +
                "left join Person p on p.id=doctId  " +
                "where qT.code='external' and q.value>0 " +
                "group by  doctId, date ) as T2  " +
                "left join rbSpeciality s on s.id=specId " +
                "where s.id is not null  " +
                "group by specId, s.code, s.OKSOName;";

        MapSqlParameterSource parameters = getCommonSQLParameters(lpuId);


        List<Spesiality> specList = jdbcTemplateNP.query(query, parameters, (rs, i) -> {
            Spesiality s = new Spesiality();
            s.setIdSpesiality(rs.getString("specId"));
            s.setNameSpesiality(rs.getString("specName"));
            s.setFerIdSpesiality(rs.getString("ferCode"));
            s.setCountFreeTicket(rs.getInt("free"));
            s.setCountFreeParticipantIE(rs.getInt("available"));
            if (rs.getDate("minDate")!=null) s.setNearestDate(Utils.toXMLDate(rs.getDate("minDate")));
            if (rs.getDate("maxDate")!=null) s.setLastDate(Utils.toXMLDate(rs.getDate("maxDate")));
            return s;
        });
        return specList;
    }

    public List<Doctor> getDoctorList(Integer lpuId, String specId) {
        String query = "select p.id as docId, concat('(',p.office2,' к.',p.office,') ',p.lastName, ' ',  p.firstName, ' ',  p.patrName) as `fio`, p.SNILS as SNILS, GROUP_CONCAT(DISTINCT org.code ORDER BY org.code) as uch, " +
                "sum(total) as total, sum(free) as free, sum(freeI) as available, sum(totalI) as sTotalI, 0 as patientAge, p.speciality_id as specId, min(dateI) as minDate, max(dateI) as maxDate from ( " +
                "select doctId, date, sum(queueI) as qExt, sum(free) as free, sum(totalT) as total, q.value, IF(floor((sum(totalT)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(totalT)*q.value)/100)-sum(queueI),sum(free)),0)  as freeI, floor((sum(totalT)*q.value)/100) as totalI, " +
                "IF(IF(floor((sum(totalT)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(totalT)*q.value)/100)-sum(queueI),sum(free)),0)>0,date, null) as dateI from (  " +
                "select  " +
                "Event.setPerson_id as doctId,  DATE(Event.setDate) as date, 1 as totalT, IF(atQueue.value IS NULL,1,0) as free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) as queueI  " +
                "from Action " +
                "left join ActionType ON ActionType.id = Action.actionType_id " +
                "left join Event ON (Event.id = Action.event_id and Event.eventType_id=:eventTypeId) " +
                "left join Person ON Person.id = Event.setPerson_id " +
                "left join Action aT ON aT.id=Action.id " +
                "left join ActionProperty atP on (atP.action_id=aT.id and atP.type_id=:actionPTimesId)   " +
                "left join ActionProperty_Time atTime on atTime.id=atP.id  " +
                "left join ActionProperty aqP on (aqP.action_id=aT.id and aqP.type_id=:actionPQueueId)   " +
                "left join ActionProperty_Action atQueue on (atQueue.id=aqP.id and atQueue.`index`=atTime.`index`) " +
                "left join Action qA on qA.id=atQueue.value  " +
                "where  " +
                "Event.deleted=0  " +
                "AND Action.deleted=0  " +
                "AND atTime.value is not null  " +
                "AND Action.actionType_id=:actionTypeId  " +
                "AND Event.setPerson_id IN (SELECT id from Person p where p.availableForExternal=1 and p.speciality_id=:specId and p.orgStructure_id in (:orgs) and p.retireDate is null) " +
                "AND Event.setDate >= :dtBegin  " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate) " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate),DATETIME),'08:00')<=ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 and atP.id is not null  " +
                "AND (aqP.deleted=0 or aqP.id is null)  " +
                "AND atTime.value IS NOT NULL " +
                "UNION ALL " +
                "select id as doctId, null as date, 0 as totalT, 0 as free, 0 as queueI from Person where availableForExternal=1 and speciality_id=:specId and orgStructure_id in (:orgs) and retireDate is null  " +
                ") as T  " +
                "left join PersonPrerecordQuota q on q.person_id=doctId " +
                "left join rbPrerecordQuotaType qT on qT.id=q.quotaType_id  " +
                "left join Person p on p.id=doctId  " +
                "where qT.code='external' and q.value>0 " +
                "group by  doctId, date ) as T2  " +
                "left join Person p on p.id=doctId  " +
                "left join OrgStructure org on ( (org.id=p.orgStructure_id or org.chief_id=p.id and org.headNurse_id=p.id)  and org.isArea>0) " +
                "group by p.id;";

        MapSqlParameterSource parameters = getCommonSQLParameters(lpuId);
        parameters.addValue("specId", specId);

        List<Doctor> doctList = jdbcTemplateNP.query(query, parameters, (rs, i) -> {
            Doctor d = new Doctor();
            d.setIdDoc(rs.getString("docId"));
            d.setName(rs.getString("fio"));
            try {
                d.setComment(queueService.getAbsenceReason(rs.getInt("docId")));
            } catch (Exception ex) {}

            Integer dSpec = rs.getInt("specId");
            if (dSpec == 2 || dSpec == 44 || dSpec == 45 || dSpec == 52 || dSpec == 78)
                d.setAriaNumber(rs.getString("uch"));

            d.setSnils(Utils.formatSNILS(rs.getString("SNILS")));

            d.setCountFreeTicket(rs.getInt("free"));
            d.setCountFreeParticipantIE(rs.getInt("available"));

            if (rs.getDate("minDate")!=null) d.setNearestDate(Utils.toXMLDate(rs.getDate("minDate")));
            if (rs.getDate("maxDate")!=null) d.setLastDate(Utils.toXMLDate(rs.getDate("maxDate")));

            return d;
        });
        return doctList;

    }

    public List<Date> getAvailableDates(String doctId, Date dtBegin, Date dtEnd) {
        String query = "SELECT DATE FROM ( " +
                "SELECT DATE, SUM(queueI) AS qExt, SUM(free) AS free, SUM(totalT) AS total, q.value, IF(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI)>0, LEAST(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI), SUM(free)),0) AS freeI, FLOOR((SUM(totalT)*q.value)/100) AS totalI " +
                "FROM ( " +
                "SELECT DATE(Event.setDate) AS DATE, 1 AS totalT, IF(atQueue.value IS NULL,1,0) AS free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) AS queueI " +
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
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate)  " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate), DATETIME),'08:00')<= ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL  " +
                ") AS T " +
                "LEFT JOIN PersonPrerecordQuota q ON q.person_id=:doctId " +
                "LEFT JOIN rbPrerecordQuotaType qT ON qT.id=q.quotaType_id " +
                "WHERE qT.code='external' and q.value>0 " +
                "GROUP BY DATE) AS T2 " +
                "WHERE freeI > 0";

        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();
        parameters.addValue("doctId", doctId);
        parameters.addValue("dtBegin", dtBegin);
        parameters.addValue("dtEnd", dtEnd);

        return jdbcTemplateNP.query(query, parameters, (resultSet, i) -> resultSet.getDate("date"));

    }

    public List<Appointment> getAvailableAppointments(String doctId, Date dtBegin, Date dtEnd) {
        List<Date> dt;
        if (dtEnd==null) {
            dt = getAvailableDates(doctId, dtBegin, dtBegin);
        } else {
            dt = getAvailableDates(doctId, dtBegin, dtEnd);
        }
        if (dt.isEmpty()) return new ArrayList<>();
        return getAvailableAppointments(doctId,dt);
    }


    public List<Appointment> getAvailableAppointments(String doctId, List<Date> dt) {

        String query = "SELECT DATE(Event.setDate) AS DATE, atTime.value, CONCAT(DATE(Event.setDate),'T',atTime.value) as dt " +
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
                "AND Event.setDate IN (:dt)  " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL AND atQueue.value IS NULL " +
                "ORDER BY DATE(Event.setDate), atTime.value";

        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();
        parameters.addValue("doctId", doctId);
        parameters.addValue("dt", dt);

        return jdbcTemplateNP.query(query, parameters, (rs, i) -> {
            Appointment app = new Appointment();
            app.setVisitStart(Utils.toXMLDateTime(rs.getString("dt")));
            app.setVisitEnd(app.getVisitStart());
            app.setIdAppointment(String.valueOf(doctId) + "_" + rs.getString("dt").replace("T", "_"));
            return app;
        });
    }

    @Cacheable(cacheNames = "workingTime")
    public List<WorkingTime> getWorkingTime(String doctId, Date dtBegin, Date dtEnd) {
        String query = "SELECT CONCAT(DATE,'T', MIN(tTime)) as dtMin, CONCAT(DATE,'T', MAX(tTime)) as dtMax,  DATE as dt , MIN(tTime) as minT, MAX(tTime) as maxT, SUM(queueI) AS qExt, SUM(free) AS free, SUM(totalT) AS total, q.value, IF(ROUND((SUM(totalT)*q.value)/100)- SUM(queueI)>0, LEAST(ROUND((SUM(totalT)*q.value)/100)- SUM(queueI), SUM(free)),0) AS freeI, ROUND((SUM(totalT)*q.value)/100) AS totalI " +
                "FROM ( " +
                "SELECT Event.setPerson_id AS doctId, DATE(Event.setDate) AS DATE, 1 AS totalT, IF(atQueue.value IS NULL,1,0) AS free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) AS queueI, atTime.value as tTime " +
                " " +
                "from Action " +
                "left join ActionType ON ActionType.id = Action.actionType_id " +
                "left join Event ON (Event.id = Action.event_id and Event.eventType_id=:eventTypeId) " +
                "left join Person ON Person.id = Event.setPerson_id " +
                "left join Action aT ON aT.id=Action.id " +
                "left join ActionProperty atP on (atP.action_id=aT.id and atP.type_id=:actionPTimesId)   " +
                "left join ActionProperty_Time atTime on atTime.id=atP.id  " +
                "left join ActionProperty aqP on (aqP.action_id=aT.id and aqP.type_id=:actionPQueueId)   " +
                "left join ActionProperty_Action atQueue on (atQueue.id=aqP.id and atQueue.`index`=atTime.`index`) " +
                "left join Action qA on qA.id=atQueue.value  " +
                "where  " +
                "Event.deleted=0  " +
                "AND Action.deleted=0  " +
                "AND atTime.value is not null  " +
                "AND Action.actionType_id=:actionTypeId  " +
                "AND Event.setPerson_id IN ( " +
                "SELECT id " +
                "FROM Person p " +
                "WHERE p.availableForExternal=1 AND p.id=:doctId AND p.retireDate IS NULL) AND Event.setDate between :dtBegin and :dtEnd AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate) AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate), DATETIME),'08:00')<= ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays)) AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL  " +
                ") AS T " +
                "LEFT JOIN PersonPrerecordQuota q ON q.person_id=doctId " +
                "LEFT JOIN rbPrerecordQuotaType qT ON qT.id=q.quotaType_id " +
                "LEFT JOIN Person p ON p.id=doctId " +
                "WHERE qT.code='external' and q.value>0 " +
                "GROUP BY  DATE";

        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();
        parameters.addValue("doctId", doctId);
        parameters.addValue("dtBegin", dtBegin);
        parameters.addValue("dtEnd", dtEnd);

        return jdbcTemplateNP.query(query, parameters, (rs, i) -> {
            WorkingTime w = new WorkingTime();
            w.setVisitStart(Utils.toXMLDateTime(rs.getString("dtMin")));
            w.setVisitEnd(Utils.toXMLDateTime(rs.getString("dtMax")));
            if (rs.getInt("freeI") > 0) {
                w.setRecordableDay(true);
            } else {
                w.setRecordableDay(false);
                if (rs.getInt("free") > 0) {
                    w.setDenyCause("Остались талоны для записи по телефону");
                } else {
                    w.setDenyCause("Талоны закончились / отсутствуют");
                }
            }
            return w;
        });


    }

    public List<Essence> getDocListFullTree(Integer lpuId) {
        String query = "select p.id as doctId, " +
                "concat(p.lastName, ' ',  p.firstName, ' ',  p.patrName) as `fio`, " +
                "s.id as specId, " +
                "s.OKSOName as specName, " +
                "o.name as uch " +
                "from Person p " +
                "left join rbSpeciality s on p.speciality_id = s.id " +
                "left join OrgStructure o on p.orgStructure_id = o.id " +
                "where " +
                "p.availableForExternal = 1 " +
                "and p.orgStructure_id in (:orgs) " +
                "and not isnull(s.id) " +
                "order by s.name, fio;";

        MapSqlParameterSource parameters = getCommonSQLParameters(lpuId);

        List<Map<String, Object>> res = jdbcTemplateNP.queryForList(query,parameters);

        Map<Integer,List<Map<String, Object>>> grp = res.stream().collect(groupingBy(o -> (Integer) o.get("specId")));

        List<Essence> doctList = new ArrayList<>();

        for (Integer specId : grp.keySet()) {
            Essence e  = new Essence();
            e.setIdEssence(specId.toString());
            e.setNameEssence((String) grp.get(specId).get(0).get("specName"));
            ArrayOfEssence docts = new ArrayOfEssence();
            e.setListEssence(docts);
            for (Map<String, Object> doct : grp.get(specId)) {
                Essence d = new Essence();
                d.setIdEssence(doct.get("doctId").toString());
                d.setNameEssence(doct.get("fio").toString());
                docts.getEssence().add(d);
            }
            doctList.add(e);
        }

        return doctList;
    }

    public List<PatientsArea> getPatientsAreas(String lpuId, String clientId) {
        String query = "SELECT aName, MIN(aType) AS aType, MIN(aNet) AS aNet " +
                "FROM ( " +
                "SELECT os.name AS aName, os.isArea AS aType, os.net_id AS aNet " +
                "FROM ClientAddress ca " +
                "LEFT JOIN Address adr ON adr.id=ca.address_id " +
                "LEFT JOIN OrgStructure_Address oa ON (oa.house_id=adr.house_id AND (oa.firstFlat=0 OR oa.firstFlat<=adr.flat) AND (oa.lastFlat=0 OR oa.lastFlat>=adr.flat)) " +
                "LEFT JOIN OrgStructure os ON os.id=oa.master_id " +
                "JOIN Client cl ON cl.id=:clientId " +
                "WHERE os.deleted=0 AND os.isArea>0  " +
                "AND ca.id=(SELECT IF(getClientLocAddressId(:clientId) IS NOT NULL, getClientLocAddressId(:clientId), getClientRegAddressId(:clientId)) AS adrId)  " +
                "AND  ( (os.net_id=1 AND age(cl.birthDate, NOW())>=18) OR (os.net_id=2 AND age(cl.birthDate, NOW())<18) OR (os.net_id=3 AND cl.sex=2) AND age(cl.birthDate, NOW())>=18 ) " +
                "AND (os.id in (:orgs) OR :lpuId=-1) " +
                "UNION " +
                "SELECT os.name AS aName, 999 AS aType, os.net_id AS aNet " +
                "FROM Client cl " +
                "LEFT JOIN Person doct ON doct.id=cl.attendingPerson_id " +
                "LEFT JOIN OrgStructure os ON os.id=doct.orgStructure_id " +
                "WHERE cl.id=:clientId AND os.deleted=0 AND os.isArea>0 AND os.id IS NOT NULL AND (os.id in (:orgs) OR :lpuId=-1) ) AS T " +
                "GROUP BY aName;";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("clientId", clientId);
        if (lpuId!=null) {
            parameters.addValue("orgs", queueService.getOrgStructs(misConfig.getOrgIdByMisId(Integer.valueOf(lpuId))));
            parameters.addValue("lpuId",1);
        } else {
            parameters.addValue("orgs", new Integer[]{-1});
            parameters.addValue("lpuId",-1);
        }

        return jdbcTemplateNP.query(query,parameters, (rs, i) -> {
            PatientsArea pa = new PatientsArea();
            pa.setArea(rs.getString("aName"));
            pa.setAreaType(AreaType.ТЕРАПЕВТИЧЕСКИЙ);
            Integer at = rs.getInt("aType");
            if (at==2) pa.setAreaType(AreaType.ПЕДИАТРИЧЕСКИЙ);
            if (at==3) pa.setAreaType(AreaType.АКУШЕРСКИЙ);
            if (at==4) pa.setAreaType(AreaType.ВРАЧ_ОБЩЕЙ_ПРАКТИКИ);
            if (at==5) pa.setAreaType(AreaType.КОМПЛЕКСНЫЙ);
            if (at==999) pa.setAreaType(AreaType.ПРИПИСНОЙ);
            return pa;
        });
    }

    public List<Speciality3> getAvailableDoctors(Integer lpuId, String idPat, String specId, String ferSpecId) {

        String query = " select p.id as docId, spec.code as ferCode, spec.OKSOName as specName, concat('(',p.office2,' к.',p.office,') ',p.lastName, ' ',  p.firstName, ' ',  p.patrName) as `fio`, p.SNILS as SNILS, GROUP_CONCAT(DISTINCT org.code ORDER BY org.code) as uch, " +
                "sum(total) as total, sum(free) as free, sum(freeI) as available, sum(totalI) as sTotalI, 0 as patientAge, p.speciality_id as specId, min(dateI) as minDate, max(dateI) as maxDate from ( " +
                "select doctId, date, sum(queueI) as qExt, sum(free) as free, sum(totalT) as total, q.value, IF(floor((sum(totalT)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(totalT)*q.value)/100)-sum(queueI),sum(free)),0)  as freeI, floor((sum(totalT)*q.value)/100) as totalI, " +
                "IF(IF(floor((sum(totalT)*q.value)/100)-sum(queueI)>0, LEAST(floor((sum(totalT)*q.value)/100)-sum(queueI),sum(free)),0)>0,date, null) as dateI from (  " +
                "select  " +
                "Event.setPerson_id as doctId,  DATE(Event.setDate) as date, 1 as totalT, IF(atQueue.value IS NULL,1,0) as free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) as queueI  " +
                "from Action " +
                "left join ActionType ON ActionType.id = Action.actionType_id " +
                "left join Event ON (Event.id = Action.event_id and Event.eventType_id=:eventTypeId) " +
                "left join Person ON Person.id = Event.setPerson_id " +
                "left join Action aT ON aT.id=Action.id " +
                "left join ActionProperty atP on (atP.action_id=aT.id and atP.type_id=:actionPTimesId)   " +
                "left join ActionProperty_Time atTime on atTime.id=atP.id  " +
                "left join ActionProperty aqP on (aqP.action_id=aT.id and aqP.type_id=:actionPQueueId)   " +
                "left join ActionProperty_Action atQueue on (atQueue.id=aqP.id and atQueue.`index`=atTime.`index`) " +
                "left join Action qA on qA.id=atQueue.value  " +
                "left join rbSpeciality spec on Person.speciality_id = spec.id " +
                "where  " +
                "Event.deleted=0  " +
                "AND Action.deleted=0  " +
                "AND atTime.value is not null  " +
                "AND Action.actionType_id=:actionTypeId  " +
                "AND Event.setPerson_id IN (SELECT id from Person p where p.availableForExternal=1 and (p.speciality_id=:specId OR :specId=-1) and (spec.code=:ferSpecId OR :ferSpecId=-1) and (p.orgStructure_id in (:orgs) OR :lpuId=-1) and p.retireDate is null) " +
                "AND Event.setDate >= :dtBegin  " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate) " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate),DATETIME),'08:00')<=ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 and atP.id is not null  " +
                "AND (aqP.deleted=0 or aqP.id is null)  " +
                "AND atTime.value IS NOT NULL " +
                "UNION ALL " +
                "select p.id as doctId, null as date, 0 as totalT, 0 as free, 0 as queueI from Person p " +
                "left join rbSpeciality spec on p.speciality_id = spec.id " +
                "where p.availableForExternal=1 and (p.speciality_id=:specId OR :specId=-1) and (spec.code=:ferSpecId OR :ferSpecId=-1) and (p.orgStructure_id in (:orgs) OR :lpuId=-1) and p.retireDate is null  " +
                ") as T  " +
                "left join PersonPrerecordQuota q on q.person_id=doctId " +
                "left join rbPrerecordQuotaType qT on qT.id=q.quotaType_id  " +
                "left join Person p on p.id=doctId  " +
                "where qT.code='external' and q.value>0 " +
                "group by  doctId, date ) as T2  " +
                "left join Person p on p.id=doctId  " +
                "left join rbSpeciality spec on p.speciality_id = spec.id " +
                "left join OrgStructure org on ( (org.id=p.orgStructure_id or org.chief_id=p.id or org.headNurse_id=p.id)  and org.isArea>0) " +
                "where isClientEligibleApp(:clientId, p.id)=1 and p.id > -1 " +
                "group by p.id;";

        String queryMin = "select p.id as docId, spec.code as ferCode, spec.name as specName, concat('(',p.office2,' к.',p.office,') ',p.lastName, ' ',  p.firstName, ' ',  p.patrName) as `fio`, p.SNILS as SNILS, GROUP_CONCAT(DISTINCT org.code ORDER BY org.code) as uch, " +
                "0 as total, 0 as free, 0 as available, 0 as sTotalI, 0 as patientAge, p.speciality_id as specId, NULL as minDate, NULL as maxDate " +
                "from Person p " +
                "left join PersonPrerecordQuota q on q.person_id=p.id " +
                "left join rbPrerecordQuotaType qT on qT.id=q.quotaType_id  " +
                "left join rbSpeciality spec on p.speciality_id = spec.id " +
                "left join OrgStructure org on ( (org.id=p.orgStructure_id or org.chief_id=p.id and org.headNurse_id=p.id)  and org.isArea>0) " +
                "where isClientEligibleApp(:clientId, p.id)=1 and qT.code='external' and q.value>0 " +
                "and p.availableForExternal=1 and (p.speciality_id=:specId OR :specId=-1) and (spec.code=:ferSpecId OR :ferSpecId=-1) and (p.orgStructure_id in (:orgs) OR :lpuId=-1) and p.retireDate is null " +
                "group by p.id;";


        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();

        if (lpuId!=null) {
            parameters.addValue("orgs", queueService.getOrgStructs(misConfig.getOrgIdByMisId(lpuId)));
            parameters.addValue("lpuId",1);
        } else {
            parameters.addValue("orgs", new Integer[]{-1});
            parameters.addValue("lpuId",-1);
        }

        if (specId!=null && !specId.isEmpty()) {
            parameters.addValue("specId", Integer.valueOf(specId));
        } else {
            parameters.addValue("specId", -1);
        }
        if (ferSpecId!=null && !ferSpecId.isEmpty()) {
            parameters.addValue("ferSpecId", Integer.valueOf(ferSpecId));
        } else {
            parameters.addValue("ferSpecId", -1);
        }
        parameters.addValue("clientId", Integer.valueOf(idPat));

        List<Doctor3WithSpec> doctList = jdbcTemplateNP.query(queryMin, parameters, (rs, i) -> {
            Doctor3 d = new Doctor3();
            d.setIdDoc(rs.getString("docId"));
            d.setName(rs.getString("fio"));

            Integer dSpec = rs.getInt("specId");
            if (dSpec == 2 || dSpec == 44 || dSpec == 45 || dSpec == 52 || dSpec == 78)
                d.setArea(rs.getString("uch"));

            d.setSnils(Utils.formatSNILS(rs.getString("SNILS")));

            d.setCountFreeTicket(rs.getInt("free"));
            d.setCountFreeParticipantIE(rs.getInt("available"));

            if (rs.getDate("minDate")!=null) d.setNearestDate(Utils.toXMLDate(rs.getDate("minDate")));
            if (rs.getDate("maxDate")!=null) d.setLastDate(Utils.toXMLDate(rs.getDate("maxDate")));

            SpecTuple spec = new SpecTuple(rs.getString("specId"), rs.getString("ferCode"), rs.getString("specName"));
            Doctor3WithSpec ds = new Doctor3WithSpec(d,spec);

            return ds;
        });

        Map<SpecTuple,List<Doctor3WithSpec>> ds = doctList.stream().collect(groupingBy(o -> o.getSpec()));

        List<Speciality3> res = new ArrayList<>();
        for (SpecTuple s : ds.keySet()) {
            Speciality3 spec = new Speciality3();
            spec.setIdSpeciality(s.getSpecId());
            spec.setFerIdSpeciality(s.getFerSpecId());
            spec.setNameSpeciality(s.getSpecName());
            ArrayOfDoctor3 docs = new ArrayOfDoctor3();
            List<Doctor3> doctors = ds.get(s).stream().map(o -> o.getDoctor()).collect(Collectors.toList());
            docs.getDoctor3().addAll(doctors);

            spec.setCountFreeParticipantIE(doctors.stream().mapToInt(o->o.getCountFreeParticipantIE()).sum());
            spec.setCountFreeTicket(doctors.stream().mapToInt(o->o.getCountFreeTicket()).sum());

            Optional<Date> lDate = doctors.stream()
                    .filter(o-> o.getLastDate()!=null)
                    .map( o-> Utils.fromXMLDate(o.getLastDate()))
                    .max(Comparator.naturalOrder());
            if (lDate.isPresent()) spec.setLastDate(Utils.toXMLDate(lDate.get()));

            Optional<Date> nDate = doctors.stream()
                    .filter(o-> o.getNearestDate()!=null)
                    .map( o-> Utils.fromXMLDate(o.getNearestDate()))
                    .min(Comparator.naturalOrder());
            if (nDate.isPresent()) spec.setNearestDate(Utils.toXMLDate(nDate.get()));

            spec.setDocs(docs);
            res.add(spec);
        }
        return res;
    }


    public District getDistrict() {
        District district = new District();
        district.setDistrictName(misConfig.getMisDistrict().get("districtName"));
        district.setIdDistrict(new Integer(misConfig.getMisDistrict().get("idDistrict")));
        district.setOkato(new Double(misConfig.getMisDistrict().get("okato")));
        return district;
    }

    public List<Clinic> getLPUs() {
        List<Clinic> lpus = new ArrayList<>();
        for (Lpu lpu : misConfig.getLpus()) {
            lpus.add(jdbcTemplate.queryForObject("select * from OrgStructure where id=?", new Object[]{lpu.getOrgId()}, (rs, i) -> {
               Clinic clinic = new Clinic();
               clinic.setIdLPU(lpu.getMisId());
               clinic.setDescription("");
               clinic.setIsActive(true);
               clinic.setLPUFullName(rs.getString("name"));
               clinic.setLPUShortName(lpu.getName());
               clinic.setDistrict(new Integer(misConfig.getMisDistrict().get("idDistrict")));
               clinic.setLPUType(1);
               return clinic;
            }));
        }
        return lpus;
    }

    public InspectDoctorsReferral2Result inspectDoctorsReferral2(InspectDoctorsReferral2 ref) {
        String query = "SELECT DATE, docId FROM ( " +
                "SELECT docId, DATE, SUM(queueI) AS qExt, SUM(free) AS free, SUM(totalT) AS total, q.value, IF(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI)>0, LEAST(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI), SUM(free)),0) AS freeI, FLOOR((SUM(totalT)*q.value)/100) AS totalI " +
                "FROM ( " +
                "SELECT Event.setPerson_id as docId, DATE(Event.setDate) AS DATE, 1 AS totalT, IF(atQueue.value IS NULL,1,0) AS free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) AS queueI " +
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
                "AND Event.setPerson_id IN (SELECT id from Person p where p.speciality_id=:specId and p.orgStructure_id in (:orgs) and p.retireDate is null and isClientEligibleApp(:clientId, p.id)=1 ) " +
                "AND Event.setDate>=CURRENT_DATE() " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate)  " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate), DATETIME),'08:00')<= ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL  " +
                ") AS T " +
                "LEFT JOIN PersonPrerecordQuota q ON q.person_id=T.docId " +
                "LEFT JOIN rbPrerecordQuotaType qT ON qT.id=q.quotaType_id " +
                "WHERE qT.code='ref' and q.value>0 " +
                "GROUP BY docId, DATE) AS T2 " +
                "WHERE freeI > 0";

        Person person = ref.getAttachedReferral().getReferral().getPatient().getPerson();
        String idPat = ptService.checkPatient(person);

        InspectDoctorsReferral2Result res = new InspectDoctorsReferral2Result();
        res.setIdLpu(ref.getIdLpu());
        res.setIdPat(idPat);
        ArrayOfSpeciality2 spec  = new ArrayOfSpeciality2();

        List<Speciality2> specs = jdbcTemplate.query("SELECT s.code as ferCode, s.id as specId, s.OKSOName as specName from rbSpeciality s " +
                "JOIN rbMedicalAidProfile p on p.name=s.OKSOName " +
                "WHERE p.netrica_Code=?", new Object[]{ref.getAttachedReferral().getReferral().getReferralInfo().getProfileMedService().getCode()}, (rs, i) -> {
            Speciality2 s = new Speciality2();
            s.setFerIdSpeciality(rs.getString("ferCode"));
            s.setIdSpeciality(rs.getString("specId"));
            s.setNameSpeciality(rs.getString("specName"));
            s.setListDoctor(new ArrayOfDoctor2());
            return s;
        });

        for (Speciality2 s : specs) {
            s.getListDoctor().getDoctor2().addAll(getAvailableAppointmentsPAorRef(ref.getIdLpu(),s.getIdSpeciality(),idPat,query).getListDoctor().getDoctor2());
            spec.getSpeciality2().add(s);
        }
        res.setListSpeciality(spec);
        res.setSuccess(true);
        return res;
    }

    public Speciality2 getAvailableAppointmentsPA(Integer lpuId, String specId, String clientId) {
        String query = "SELECT DATE, docId FROM ( " +
                "SELECT docId, DATE, SUM(queueI) AS qExt, SUM(free) AS free, SUM(totalT) AS total, q.value, IF(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI)>0, LEAST(FLOOR((SUM(totalT)*q.value)/100)- SUM(queueI), SUM(free)),0) AS freeI, FLOOR((SUM(totalT)*q.value)/100) AS totalI " +
                "FROM ( " +
                "SELECT Event.setPerson_id as docId, DATE(Event.setDate) AS DATE, 1 AS totalT, IF(atQueue.value IS NULL,1,0) AS free, IF (qA.setPerson_id IS NULL AND atQueue.value IS NOT NULL,1,0) AS queueI " +
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
                "AND Event.setPerson_id IN (SELECT id from Person p where p.availableForExternal=1 and p.speciality_id=:specId and p.orgStructure_id in (:orgs) and p.retireDate is null and isClientEligibleApp(:clientId, p.id)=1 ) " +
                "AND Event.setDate>=CURRENT_DATE() " +
                "AND (Person.lastAccessibleTimelineDate IS NULL OR Person.lastAccessibleTimelineDate = '0000-00-00' OR DATE(Event.setDate)<=Person.lastAccessibleTimelineDate)  " +
                "AND (Person.timelineAccessibleDays IS NULL OR Person.timelineAccessibleDays <= 0 OR ADDTIME(CONVERT(DATE(Event.setDate), DATETIME),'08:00')<= ADDDATE(CURRENT_TIMESTAMP(), Person.timelineAccessibleDays))  " +
                "AND atP.deleted=0 AND atP.id IS NOT NULL AND (aqP.deleted=0 OR aqP.id IS NULL) AND atTime.value IS NOT NULL  " +
                ") AS T " +
                "LEFT JOIN PersonPrerecordQuota q ON q.person_id=T.docId " +
                "LEFT JOIN rbPrerecordQuotaType qT ON qT.id=q.quotaType_id " +
                "WHERE qT.code='external' and q.value>0 " +
                "GROUP BY docId, DATE) AS T2 " +
                "WHERE freeI > 0";

        return getAvailableAppointmentsPAorRef(lpuId,specId,clientId,query);
    }

    private Speciality2 getAvailableAppointmentsPAorRef(Integer lpuId, String specId, String clientId, String query) {

        MapSqlParameterSource parameters = getCommonSQLParameters(lpuId);
        parameters.addValue("specId", specId);
        parameters.addValue("clientId", clientId);

        List<Doctor2Tuple> docsDt = jdbcTemplateNP.query(query, parameters, (rs, i) -> new Doctor2Tuple(rs.getInt("docId"),rs.getDate("date")));
        Map<Integer, List<Date>> docs = docsDt.stream().collect(groupingBy(o -> o.getDocId(), mapping(o -> o.getDt(), toList())));

        Speciality2 spec = new Speciality2();
        ArrayOfDoctor2 arrDoc = new ArrayOfDoctor2();
        spec.setListDoctor(arrDoc);

        for (Integer docId : docs.keySet()) {
            Doctor2 doc = jdbcTemplate.queryForObject("SELECT p.id as docId, spec.code as ferCode, spec.id as specId, spec.name as specName, concat('(',p.office2,' к.',p.office,') ',p.lastName, ' ',  p.firstName, ' ',  p.patrName) as `fio`, p.SNILS as SNILS from Person p " +
                    "LEFT JOIN rbSpeciality spec on spec.id=p.speciality_id where p.id=?;", new Object[]{docId}, (rs, i) -> {
                Doctor2 d = new Doctor2();
                d.setIdDoc(rs.getString("docId"));
                d.setName(rs.getString("fio"));
                d.setSnils(Utils.formatSNILS(rs.getString("SNILS")));

                spec.setFerIdSpeciality(rs.getString("ferCode"));
                spec.setIdSpeciality(rs.getString("specId"));
                spec.setNameSpeciality(rs.getString("specName"));
                return d;
            });
            ArrayOfAppointment arrApp = new ArrayOfAppointment();
            arrApp.getAppointment().addAll(getAvailableAppointments(docId.toString(), docs.get(docId)));
            arrDoc.getDoctor2().add(doc);
            doc.setListAppointment(arrApp);
        }

        return spec;
    }

    @Cacheable(cacheNames = "absenceReason")
    public String getAbsenceReason(Integer doctId) {
        String query = "SELECT reason.shortName as name, DATE(Event.setDate) AS dt,  " +
                "ADDDATE(DATE(Event.setDate),INTERVAL 1 DAY) as dtNext1, " +
                "DATE(CASE DAYOFWEEK(ADDDATE(DATE(Event.setDate),INTERVAL 1 DAY))  " +
                " WHEN 1 THEN ADDDATE(DATE(Event.setDate),INTERVAL 2 DAY) " +
                " WHEN 7 THEN ADDDATE(DATE(Event.setDate),INTERVAL 3 DAY)\t " +
                " ELSE ADDDATE(DATE(Event.setDate),INTERVAL 1 DAY)  " +
                "END) as dtNext2 " +
                "FROM Action " +
                "JOIN ActionType ON ActionType.id = Action.actionType_id " +
                "JOIN Event ON (Event.id = Action.event_id AND Event.eventType_id=5) " +
                "JOIN Action aT ON aT.id=Action.id " +
                "JOIN ActionProperty atP ON (atP.action_id=aT.id AND atP.type_id=32) " +
                "JOIN ActionProperty_rbReasonOfAbsence rAbs ON rAbs.id=atP.id " +
                "JOIN rbReasonOfAbsence reason ON reason.id=rAbs.value " +
                "WHERE Event.deleted=0 AND Action.deleted=0 AND rAbs.value IS NOT NULL  AND reason.shortName IS NOT NULL  " +
                "AND Action.actionType_id=18  " +
                "AND Event.setPerson_id=? " +
                "AND  Event.setDate>= CURRENT_DATE() AND atP.deleted=0 AND atP.id IS NOT NULL  " +
                "ORDER BY DATE(Event.setDate)";
        List<AbsenceReason> absenceReasons = jdbcTemplate.query(query,new Object[]{doctId}, (rs, i)->{
            AbsenceReason r = new AbsenceReason();
            r.setDt(rs.getDate("dt").toLocalDate());
            r.setDtNext1(rs.getDate("dtNext1").toLocalDate());
            r.setDtNext2(rs.getDate("dtNext2").toLocalDate());
            r.setReason(rs.getString("name"));
            return r;
        });

        Map<String,List<AbsenceReason>> reasonsGroup =  absenceReasons.stream().collect(groupingBy(AbsenceReason::getReason));
        reasonsGroup.values().stream().forEach((l)->l.sort((o1,o2)-> o1.getDt().compareTo(o2.getDt())));
        reasonsGroup.values().stream().forEach((l)-> {
            AbsenceReason lastR = null;
            Integer group = 0;
            for (AbsenceReason r : l) {
                if (lastR==null) {r.setGroup(group); lastR = r; continue;}
                if (! (r.getDt().isEqual(lastR.getDtNext1()) || r.getDt().isEqual(lastR.getDtNext2()) || (r.getDt().isAfter(lastR.getDtNext1()) && r.getDt().isBefore(lastR.getDtNext2())))) group++;
                r.setGroup(group);
                lastR = r;
            }
        });
        List<String> reasonsText = new ArrayList<>();
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("dd.MM");
        for (String reason : reasonsGroup.keySet()) {
            List<String> dtText = new ArrayList<>();
            Map<Integer, List<AbsenceReason>> dtGroup = reasonsGroup.get(reason).stream().collect(groupingBy(AbsenceReason::getGroup));
            for (List<AbsenceReason> gr : dtGroup.values()) {
                LocalDate minDt = gr.stream().min(Comparator.comparing(AbsenceReason::getDt)).get().getDt();
                LocalDate maxDt = gr.stream().max(Comparator.comparing(AbsenceReason::getDt)).get().getDt();
                if (minDt.isEqual(maxDt)) {
                    dtText.add(sdf.format(minDt));
                } else {
                    dtText.add(sdf.format(minDt)+"-"+sdf.format(maxDt));
                }
            }
            reasonsText.add(reason+": "+String.join(",",dtText));
        }
        return String.join(";",reasonsText);
    }


    public MapSqlParameterSource getCommonSQLParameters(Integer lpuId) {
        MapSqlParameterSource parameters = misConfig.getCommonSQLParameters();
        parameters.addValue("orgs", queueService.getOrgStructs(misConfig.getOrgIdByMisId(lpuId)));
        return parameters;
    }

}
