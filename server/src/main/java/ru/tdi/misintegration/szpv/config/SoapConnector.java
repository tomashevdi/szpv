package ru.tdi.misintegration.szpv.config;

import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class SoapConnector extends WebServiceGatewaySupport {

    final String ns = "http://tempuri.org/IHubService/";

    public Object callWebService(String url, String method, Object request){
        return getWebServiceTemplate().marshalSendAndReceive(url, request, new SoapActionCallback(ns+method));
    }
}