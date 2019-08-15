package ru.tdi.misintegration.szpv.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.tdi.misintegration.szpv.Utils;
import ru.tdi.misintegration.szpv.service.LoggerService;
import ru.tdi.misintegration.szpv.service.RFSZException;
import ru.tdi.misintegration.szpv.service.UserService;

import java.lang.reflect.Constructor;
import java.util.TimeZone;

@Aspect
@Component
public class LoggingAspect {

    org.slf4j.Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    LoggerService logger;

    @Autowired
    UserService userService;

    @Around("execution(* ru.tdi.misintegration.szpv.controller.SZPVController.*(..))")
    public Object logger(ProceedingJoinPoint jp)  {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTimeZone(TimeZone.getDefault());

        Object arg = jp.getArgs()[0];
        BeanWrapper wr = new BeanWrapperImpl(arg);
        String idLpu = getProperty(wr, "idLpu");
        String guid = getProperty(wr, "guid");
        String idPat = getProperty(wr, "idPat");

        if (guid==null || guid.isEmpty() || guid.equals("0")) return createErrorResponse(arg, 1, "Доступ запрещен");

        String request;
        try {
            request = mapper.writeValueAsString(arg);
        } catch (JsonProcessingException e) {
            request = e.toString();
        }

        long start_time = System.nanoTime();
        String response = "";
        Object res = null;
        Throwable exception = null;
        try {
            userService.getUsername(guid);
            res = jp.proceed();
        } catch (Throwable throwable) {
            response = throwable.toString();
            exception = throwable;
        }
        long end_time = System.nanoTime();
        Double duration = (end_time - start_time) / 1e6;

        if (res != null) {
            try {
                response = mapper.writeValueAsString(res);
            } catch (JsonProcessingException e) {
                response = e.toString();
            }
        }

        logger.log(guid, idLpu, duration.intValue(), jp.getSignature().getName(), request, response, idPat);


        if (exception != null) {
            if (exception instanceof RFSZException) {
                RFSZException rfEx = (RFSZException) exception;
                return createErrorResponse(arg, rfEx.getCode(), rfEx.getMessage());
            }
            return createErrorResponse(arg, 99, "Возникла техническая ошибка: " + exception.toString());
        }

        return res;
    }

    private Object createErrorResponse(Object arg, Integer code, String message) {
        try {
            String resultClassName = arg.getClass().getName() + "Result";
            Class<?> resultClass = Class.forName(resultClassName);
            Constructor<?> resultConst = resultClass.getConstructor();
            Object resultObj = resultConst.newInstance();

            String responseClassName = arg.getClass().getName() + "Response";
            Class<?> responseClass = Class.forName(responseClassName);
            Constructor<?> responseConst = responseClass.getConstructor();
            Object responseObj = responseConst.newInstance();

            BeanWrapper resultWR = new BeanWrapperImpl(resultObj);
            resultWR.setPropertyValue("success", false);
            resultWR.setPropertyValue("errorList", Utils.setError(code, message));

            BeanWrapper responseWR = new BeanWrapperImpl(responseObj);
            String resProp = arg.getClass().getSimpleName();
            resProp = Character.toLowerCase(resProp.charAt(0)) + resProp.substring(1);
            responseWR.setPropertyValue(resProp + "Result", resultObj);
            return responseObj;
        } catch (Throwable ex) {
            return null;
        }
    }

    private String getProperty(BeanWrapper wr, String name) {
        String res;
        try {
            Object val = wr.getPropertyValue(name);
            res = val != null ? val.toString() : "0";
        } catch (BeansException ex) {
            res = "0";
        }
        return res;
    }

}
