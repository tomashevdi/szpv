package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import ru.tdi.misintegration.szpv.Utils;
import ru.tdi.misintegration.szpv.config.Lpu;
import ru.tdi.misintegration.szpv.config.MisConfig;
import ru.tdi.misintegration.szpv.config.SoapConnector;
import ru.tdi.misintegration.szpv.model.PARequestRes;
import ru.tdi.misintegration.szpv.model.PatientRes;
import ru.tdi.misintegration.szpv.ws.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WaitingListService {

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

    public SetWaitingListResult setWaitingList(SetWaitingList wl) {
        SetWaitingListResult res = new SetWaitingListResult();
        UUID guid = UUID.randomUUID();

        MapSqlParameterSource wlParam = new MapSqlParameterSource();
        wlParam.addValue("clientId", wl.getIdPat());
        wlParam.addValue("userId", misConfig.getUserId());
        wlParam.addValue("specId", wl.getIdSpesiality());
        wlParam.addValue("orgId", misConfig.getLpuMap().get(wl.getIdLpu()).getOrgId());
        wlParam.addValue("personId", wl.getIdDoc());
        wlParam.addValue("status", 1);
        wlParam.addValue("guid", guid.toString());

        if (wl.getRule()!=null && wl.getRule().getEnd()!=null) {
            wlParam.addValue("maxDate", Utils.fromXMLDate(wl.getRule().getEnd()));
        } else {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH,10);
            wlParam.addValue("maxDate", cal.getTime());
        }

        String comment = "Телефон: "+String.valueOf(wl.getPhone())+"\n Причина: "+String.valueOf(wl.getClaim())+" \n Мин.дата приема: " +
                String.valueOf(wl.getRule().getStart()) +" \n Макс.дата приема: "+String.valueOf(wl.getRule().getEnd())+" \n GUID: "+guid.toString()+" \n Записал: "+
                userService.getUsername(wl.getGuid());
        wlParam.addValue("comment", comment);

        try {
            jdbcTemplateNP.update("INSERT INTO DeferredQueue(createDatetime, createPerson_id, modifyDatetime, modifyPerson_id, " +
                    "client_id, speciality_id, person_id, maxDate, status_id, comment, orgStructure_id) " +
                    "VALUES (NOW(),:userId,NOW(),:userId,:clientId,:specId,:personId,:maxDate,:status,:comment, :orgId)", wlParam);
            res.setSuccess(true);
            res.setGuidClaim(guid.toString());
        } catch (DataAccessException ex) {
            res.setSuccess(false);
            res.setErrorList(Utils.setError(99,"Непредвиденная ошибка"));
        }
        return res;
    }


}
