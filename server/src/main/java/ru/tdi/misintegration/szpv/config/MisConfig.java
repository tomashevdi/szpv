package ru.tdi.misintegration.szpv.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Getter
public class MisConfig {

    @Value("${mis.qEventType:5}")
    private Integer qEventType;
    @Value("${mis.qActionType:14}")
    private Integer qActionType;
    @Value("${mis.queueAPType:18}")
    private Integer qAPType;
    @Value("${mis.timesAPType:17}")
    private Integer tAPType;
    @Value("${mis.officeAPType:15}")
    private Integer officeAPType;
    @Value("${mis.appEventType:29}")
    private Integer appEventType;
    @Value("${mis.appActionType:19}")
    private Integer appActionType;

    @Value("${mis.webService}")
    String webService;
    @Value("${mis.guid}")
    String guid;

    @Value("${mis.orgId:3195}")
    private Integer orgId;
    @Value("${mis.userId}")
    private Integer userId;
    @Value("${mis.ctoUser:269}")
    private Integer ctoUserId;
    @Value("#{${mis.district}}")
    private Map<String,String> misDistrict;

    List<Lpu> lpus;
    Map<Integer,Lpu> lpuMap;
    Map<Integer,Lpu> topOrgMap;
    Map<Integer,Lpu> egisLpuMap;

    public MisConfig(@Value("${mis.lpuList}") String lpuJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            lpus = Collections.unmodifiableList(mapper.readValue( getClass().getClassLoader().getResource(lpuJson), new TypeReference<List<Lpu>>(){}));
            lpuMap = lpus.stream().collect( Collectors.toMap(Lpu::getMisId, x->x) );
            topOrgMap = lpus.stream().collect( Collectors.toMap(Lpu::getOrgId, x->x) );
            egisLpuMap = lpus.stream().collect( Collectors.toMap(Lpu::getRemoteId, x->x) );
        } catch (IOException e) {
            lpus = new ArrayList<>();
            lpuMap = new HashMap<>();
        }
    }

    public Integer getOrgIdByMisId(Integer misId) {
        return lpuMap.get(misId).getOrgId();
    }

    public MapSqlParameterSource getCommonSQLParameters() {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("eventTypeId", getQEventType());
        parameters.addValue("actionPTimesId", getTAPType());
        parameters.addValue("actionPQueueId", getQAPType());
        parameters.addValue("actionTypeId", getQActionType());
        parameters.addValue("dtBegin", new Date());
        return parameters;
    }


}
