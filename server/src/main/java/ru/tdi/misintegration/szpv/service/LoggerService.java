package ru.tdi.misintegration.szpv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoggerService {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String guid, String lpuId, Integer duration, String method, String request, String response, String idPat) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(guid);
        sb.append("] [");
        sb.append(lpuId);
        sb.append("] [");
        sb.append(duration);
        sb.append("] [");
        sb.append(method);
        sb.append("] [");
        sb.append(request);
        sb.append("] [");
        sb.append(response);
        sb.append("] [");
        sb.append(idPat);
        sb.append("]");
        log.info(sb.toString());
        try {
            jdbcTemplate.update("INSERT INTO egisz.log (user, lpuId, duration, method, request, response, idPat, ver) VALUES (?,?,?,?,?,?,?,3);", guid, lpuId, duration, method, request, response, idPat);
        } catch (Throwable ex) {
            log.debug(ex.toString());
        }
    }
}
