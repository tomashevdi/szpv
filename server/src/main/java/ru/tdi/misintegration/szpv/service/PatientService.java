package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.soap.SoapFaultException;
import ru.tdi.misintegration.szpv.ws.*;
import ru.tdi.misintegration.szpv.config.MisConfig;
import ru.tdi.misintegration.szpv.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplateNP;

    @Autowired
    MisConfig misConfig;

    @Autowired
    UserService userService;

    public List<Patient> searchPatients(Patient pat) {

        if (pat==null) throw new RFSZException(4,"Получен не полный набор данных.");

        String queryT  = "select c.id, c.SNILS, c.firstName, c.lastName, c.patrName, c.birthDate, c.sex, p.serial as polisSerial, p.number as polisNumber,  d.serial as docSerial, d.number as docNumber, " +
                " age(c.birthDate, CURRENT_DATE()) AS age " +
                "from Client c " +
                "left join ClientPolicy p on p.id=(select max(pp.id) from ClientPolicy pp where pp.client_id = c.id and pp.deleted = 0) " +
                "left join ClientDocument d on d.id =(SELECT MAX(dd.id) FROM ClientDocument dd WHERE dd.client_id = c.id and dd.deleted = 0) " +
                "where c.deleted = 0 ";

        String query = "";

        MapSqlParameterSource param = new MapSqlParameterSource();

        String val = fixString(pat.getSurname());
        if (!val.isEmpty()) {
            query+=" and c.lastName like :surname ";
            param.addValue("surname",val+"%");
        }

        val = fixString(pat.getName());
        if (!val.isEmpty()) {
            query+=" and c.firstName like :name ";
            param.addValue("name",val+"%");
        }

        val = fixString(pat.getSecondName());
        if (!val.isEmpty()) {
            query+=" and c.patrName like :secondName ";
            param.addValue("secondName",val+"%");
        }

        if (pat.getBirthday()!=null) {
            query+=" and c.birthDate = :birthDay ";
            param.addValue("birthDay", Utils.fromXMLDate(pat.getBirthday()));
        }

        val = fixString(pat.getIdPat());
        if (!val.isEmpty()) {
            query+=" and c.id = :patId ";
            param.addValue("patId",val);
        }

        val = fixString(pat.getDocumentS());
        if (!val.isEmpty()) {
            query+=" and d.serial = :docS ";
            param.addValue("docS",val);
        }
        val = fixString(pat.getDocumentN());
        if (!val.isEmpty()) {
            query+=" and d.number = :docN ";
            param.addValue("docN",val);
        }

        val = fixString(pat.getPolisS());
        if (!val.isEmpty()) {
            query+=" and p.serial = :polS ";
            param.addValue("polS",val);
        }
        val = fixString(pat.getPolisN());
        if (!val.isEmpty()) {
            query+=" and p.number = :polN and c.id IN (SELECT client_id from ClientPolicy where number = :polN) ";
            param.addValue("polN",val);
        }

        val = formatSnils(pat.getSnils());
        if (!val.isEmpty()) {
            query+=" and c.SNILS = :snils ";
            param.addValue("snils",val);
        }

        List<Integer> clientIds = new ArrayList<>();
        val = fixString(pat.getCellPhone());
        if (!val.isEmpty()) {
            clientIds.addAll(findByPhone(val,3));
        }
        val = fixString(pat.getHomePhone());
        if (!val.isEmpty()) {
            clientIds.addAll(findByPhone(val,1));
        }
        if (!clientIds.isEmpty()) {
            query += " and c.id IN (:phoneId) ";
            param.addValue("phoneId", clientIds);
        }

        if (query.isEmpty()) throw new RFSZException(4,"Получен не полный набор данных.");

        query= queryT + query + " limit 0,10; ";

        List<Patient> patients = jdbcTemplateNP.query(query, param, (rs, i) -> {
            Patient pt  = new Patient();
            pt.setIdPat(rs.getString("id"));
            pt.setSurname(rs.getString("lastName"));
            pt.setName(rs.getString("firstName"));
            pt.setSecondName(rs.getString("patrName"));
            pt.setBirthday(Utils.toXMLDate(rs.getDate("birthDate")));
            pt.setDocumentS(rs.getString("docSerial"));
            pt.setDocumentN(rs.getString("docNumber"));
            pt.setPolisS(rs.getString("polisSerial"));
            pt.setPolisN(rs.getString("polisNumber"));
            String snils = fixString(rs.getString("SNILS"));
            if (!snils.isEmpty() && snils.length()==11) {
                pt.setSnils(snils.substring(0,3)+"-"+snils.substring(3,6)+"-"+snils.substring(6,9)+" "+snils.substring(9,11));
            }
            return pt;
        });

        return patients;
    }

    public String checkPatient(Patient pat) {
        List<Patient> pats = searchPatients(pat);
        if (pats.size()>1) {
            throw new RFSZException(21, "Данные пациента не являются уникальными");
        }
        if (pats.isEmpty()) {
            throw new RFSZException(20, "Пациент с заданными параметрами не найден");
        }
        return pats.get(0).getIdPat();
    }

    public String checkPatient(Person person) {
        Patient pat = new Patient();
        pat.setSurname(person.getHumanName().getFamilyName());
        pat.setName(person.getHumanName().getGivenName());
        pat.setSecondName(person.getHumanName().getMiddleName());
        pat.setBirthday(person.getBirthDate());

        return checkPatient(pat);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> addNewPatient(Patient pat, String guid) {
        Patient patS = new Patient();
        patS.setSurname(pat.getSurname());
        patS.setName(pat.getName());
        patS.setSecondName(pat.getSecondName());
        patS.setBirthday(pat.getBirthday());
        List<Patient> patR = searchPatients(patS);
        if (patR.size()==1) return Optional.of(patR.get(0).getIdPat());
        if (patR.size()>1) {
            return Optional.empty();
        }

        MapSqlParameterSource patParam = new MapSqlParameterSource();
        patParam.addValue("lastName", pat.getSurname());
        patParam.addValue("firstName", pat.getName());
        patParam.addValue("patrName", pat.getSecondName());
        patParam.addValue("birthDate", Utils.fromXMLDate(pat.getBirthday()));
        patParam.addValue("SNILS", formatSnils(pat.getSnils()));
        patParam.addValue("notes","Пациент создан через РФ ЕГИСЗ. Пользователь: "+userService.getUsername(guid));
        patParam.addValue("notes2","Добавлено через РФ ЕГИСЗ. Пользователь: "+userService.getUsername(guid));

        patParam.addValue("documentType_id",1);
        patParam.addValue("dSerial", pat.getDocumentS());
        patParam.addValue("dNumber", pat.getDocumentN());
        patParam.addValue("pSerial", fixString(pat.getPolisS()));
        patParam.addValue("pNumber",pat.getPolisN());
        patParam.addValue("hPhone", pat.getHomePhone());
        patParam.addValue("cPhone", pat.getCellPhone());

        patParam.addValue("userId", misConfig.getUserId());

        KeyHolder client = new GeneratedKeyHolder();
        jdbcTemplateNP.update("INSERT INTO Client(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, lastName, firstName, patrName, birthDate, birthTime, sex, SNILS, " +
                "bloodType_id, bloodDate, bloodNotes, growth, weight, embryonalPeriodWeek, birthPlace, diagNames, " +
                " notes) VALUES (NOW(),:userId,NOW(),:userId,0,:lastName,:firstName,:patrName,:birthDate,'00:00',0,:SNILS,NULL,NULL,'','','','','','',:notes)",patParam, client);
        Integer clientId = client.getKey().intValue();
        patParam.addValue("clientId", clientId);

        if (!fixString(pat.getDocumentN()).isEmpty()) {
            jdbcTemplateNP.update("INSERT INTO ClientDocument(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, client_id, " +
                    "documentType_id, serial, number, `date`, origin) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,:documentType_id,:dSerial,:dNumber,NOW(),'')",patParam);
        }

        if (!fixString(pat.getPolisN()).isEmpty() ) {
            jdbcTemplateNP.update("INSERT INTO ClientPolicy(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, " +
                    "deleted, client_id, serial, number, begDate, name, note,insuranceArea) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,:pSerial,:pNumber,NOW(),'',:notes2,'')",patParam);
        }

        if (!fixString(pat.getCellPhone()).isEmpty()) {
            jdbcTemplateNP.update("INSERT INTO ClientContact(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, " +
                    "client_id, contactType_id, contact, notes) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,3, :cPhone, :notes2)",patParam);
        }

        if (!fixString(pat.getHomePhone()).isEmpty()) {
            jdbcTemplateNP.update("INSERT INTO ClientContact(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, " +
                    "client_id, contactType_id, contact, notes) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,1, :hPhone, :notes2)",patParam);
        }

        return Optional.of(clientId.toString());
    }


    public UpdatePhoneByIdPatResult updatePhoneByIdPat(String idPat, String hPhone, String cPhone, String guid) {
        UpdatePhoneByIdPatResult res = new UpdatePhoneByIdPatResult();

        MapSqlParameterSource patParam = new MapSqlParameterSource();
        patParam.addValue("clientId", idPat);
        patParam.addValue("hPhone", hPhone);
        patParam.addValue("cPhone", cPhone);
        patParam.addValue("userId", misConfig.getUserId());
        patParam.addValue("notes2","Добавлено через РФ ЕГИСЗ. Пользователь: "+userService.getUsername(guid));

        if (fixString(idPat).isEmpty()) {
            res.setSuccess(false);
            res.setErrorList(Utils.setError(4, "Пациент не указан."));
        }

        if (!fixString(cPhone).isEmpty()) {
            jdbcTemplateNP.update("INSERT INTO ClientContact(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, " +
                    "client_id, contactType_id, contact, notes) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,3, :cPhone, :notes2)",patParam);
        }

        if (!fixString(hPhone).isEmpty()) {
            jdbcTemplateNP.update("INSERT INTO ClientContact(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, deleted, " +
                    "client_id, contactType_id, contact, notes) VALUES (NOW(),:userId,NOW(),:userId,0,:clientId,1, :hPhone, :notes2)",patParam);
        }

        res.setSuccess(true);

        return res;
    }

    public GetPatientHistoryResult getPatientHistory(String idPat) {
        GetPatientHistoryResult rs = new GetPatientHistoryResult();
        String query = "select " +
                "        QueueAction.id AS `queueActionId`, " +
                "        CONCAT(DATE(QueueEvent.setDate),'T',ActionProperty_Time.value) as appDateTime, " +
                "        ActionProperty_Action.`index` AS `index`, " +
                "        concat('(',Person.office2,' к.',Person.office,') ',Person.lastName, ' ',  Person.firstName, ' ',  Person.patrName) as `fio`, " +
                "        Person.id as docId, " +
                "        rbSpeciality.name as specName, " +
                "        rbSpeciality.id as specId, " +
                "        rbSpeciality.code as ferSpecId, " +
                "        Person.SNILS as SNILS, " +
                "        QueueAction.createPerson_id AS `enqueuePersonId`, " +
                "        CONCAT(DATE(QueueAction.createDatetime),'T',TIME(QueueAction.createDatetime)) AS `enqueueDateTime` " +
                "      from Action AS QueueAction " +
                "        LEFT JOIN Event  AS QueueEvent          ON QueueEvent.id = QueueAction.event_id " +
                "        LEFT JOIN ActionProperty_Action         ON ActionProperty_Action.value = QueueAction.id " +
                "        LEFT JOIN ActionProperty                ON ActionProperty.id = ActionProperty_Action.id " +
                "        LEFT JOIN Action                        ON Action.id = ActionProperty.action_id " +
                "        LEFT JOIN ActionPropertyType AS APTTime ON APTTime.actionType_id = :actionTypeId AND APTTime.name='times' " +
                "        LEFT JOIN ActionProperty AS APTime      ON APTime.type_id = APTTime.id AND APTime.action_id = Action.id " +
                "        LEFT JOIN ActionProperty_Time           ON ActionProperty_Time.id = APTime.id AND ActionProperty_Time.`index` = ActionProperty_Action.`index` " +
                "        LEFT JOIN Event                         ON Event.id = Action.event_id " +
                "        LEFT JOIN Person                        ON QueueAction.person_id = Person.id " +
                "        LEFT JOIN rbSpeciality                  ON Person.speciality_id = rbSpeciality.id " +
                "      where " +
                "        QueueAction.deleted = 0 " +
                "        AND QueueAction.actionType_id = :qActionTypeId " +
                "        AND Person.speciality_id NOT IN (263,25,169,182,194,200)  " +
                "        AND QueueEvent.deleted = 0 " +
                "        AND QueueEvent.eventType_id= :qEventTypeId " +
                "        AND Action.deleted = 0 " +
                "      AND Action.actionType_id = :actionTypeId " +
                "        AND Event.deleted = 0 " +
                "        AND Event.eventType_id = :eventTypeId " +
                "        AND QueueEvent.setDate >= NOW()         " +
                "        AND QueueEvent.client_id = :clientId ";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("eventTypeId", misConfig.getQEventType());
        parameters.addValue("actionPTimesId", misConfig.getTAPType());
        parameters.addValue("actionPQueueId", misConfig.getQAPType());
        parameters.addValue("actionTypeId", misConfig.getQActionType());
        parameters.addValue("qEventTypeId", misConfig.getAppEventType());
        parameters.addValue("qActionTypeId", misConfig.getAppActionType());
        parameters.addValue("clientId", idPat);

        List<HistoryVisit> hvs = jdbcTemplateNP.query(query, parameters, (resSet, i) -> {
            HistoryVisit hv = new HistoryVisit();
            hv.setDateCreatedAppointment(Utils.toXMLDateTime(resSet.getString("enqueueDateTime")));
            hv.setIdAppointment(resSet.getString("queueActionId"));
            hv.setVisitStart(Utils.toXMLDateTime(resSet.getString("appDateTime")));

            Doctor doc = new Doctor();
            doc.setIdDoc(resSet.getString("docId"));
            doc.setName(resSet.getString("fio"));
            doc.setSnils(Utils.formatSNILS(resSet.getString("SNILS")));
            hv.setDoctorRendingConsultation(doc);

            Spesiality spec = new Spesiality();
            spec.setFerIdSpesiality(resSet.getString("ferSpecId"));
            spec.setIdSpesiality(resSet.getString("specId"));
            spec.setNameSpesiality(resSet.getString("specName"));
            hv.setSpecialityRendingConsultation(spec);

            User user = new User();
            user.setUserName("-");
            user.setUserPosition("отсутстствует_неопределено");
            hv.setUserCreatedAppointment(user);

            return hv;
        });

        ArrayOfHistoryVisit hvArr = new ArrayOfHistoryVisit();
        hvArr.getHistoryVisit().addAll(hvs);
        rs.setListHistoryVisit(hvArr);
        rs.setSuccess(true);

        return rs;
    }


    private List<Integer> findByPhone(String phone, Integer phoneType) {
        String query = "SELECT client_id FROM ClientContact " +
                "WHERE replace(replace(replace(replace(replace(contact, ' ',''), '(',''), ')', ''), '-', ''), '+', '') LIKE ? " +
                "AND deleted = 0 " +
                "AND contactType_id = ? limit 0,10;";
        return jdbcTemplate.query(query, new Object[] {"%"+phone, phoneType}, (resultSet, i) -> resultSet.getInt("client_id"));
    }

    private String fixString(String s) {
        if (s==null) return "";
        return s.trim().replace("%","");
    }

    private String formatSnils(String s) {
        if (s==null) return "";
        return s.trim().replace("-","").replace(" ","");
    }

}
