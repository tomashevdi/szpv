package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.tdi.misintegration.szpv.ws.GetHubUserByGuid;
import ru.tdi.misintegration.szpv.ws.GetHubUserByGuidResponse;
import ru.tdi.misintegration.szpv.ws.HubUser;
import ru.tdi.misintegration.szpv.config.MisConfig;
import ru.tdi.misintegration.szpv.config.SoapConnector;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SoapConnector soapConnector;

    @Autowired
    MisConfig misConfig;

    @Cacheable(cacheNames = "users")
    public String getUsername(String guid) {
        if (guid==null && guid.isEmpty()) return "НЕИЗВЕСТНЫЙ";

        try {
            Map<String, Object> user = jdbcTemplate.queryForMap("SELECT name, deny, denyMsg from egisz.users where user=?", guid);
            if (user.get("deny").toString().equals("1")) {
                String msg;
                if (user.get("denyMsg")==null) msg = "Доступ запрещен"; else msg = user.get("denyMsg").toString();
                throw new RFSZException(1, msg);
            }
            return user.get("name").toString();
        } catch (DataAccessException ex ) { }

        GetHubUserByGuid req = new GetHubUserByGuid();
        req.setGuid(misConfig.getGuid());
        req.setUsersGuid(guid);

        GetHubUserByGuidResponse resp = (GetHubUserByGuidResponse) soapConnector.callWebService(misConfig.getWebService(), "GetHubUserByGuid", req);
        List<HubUser> users =  resp.getGetHubUserByGuidResult().getHubUserList().getHubUser();
        if (users.isEmpty() || users.size()>1) return "НЕИЗВЕСТНЫЙ";
        HubUser user = users.get(0);

        jdbcTemplate.update("INSERT INTO egisz.users(user,name,publicGuid, role) VALUES (?,?,?,?);",guid, user.getUserName(), user.getPublicGuid(), user.getUserPosition() );

        return user.getUserName();
    }

}
