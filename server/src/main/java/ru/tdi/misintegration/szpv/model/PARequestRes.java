package ru.tdi.misintegration.szpv.model;

import lombok.Data;

import java.util.Date;

@Data
public class PARequestRes {
    String idLpu;
    String lpuName;
    String idPar;
    Date created;
    String idDoc;
    String nameDoc;
    String idSpec;
    String nameSpec;
    String claim;
    String info;
    Date startDate;
    Date endDate;
    Integer source;
    Integer status;
    PatientRes patient;
    String period;
    Date deactivateDate;
    Integer deactivateReason;
    String deactivateComment;
    Boolean invalidClient;
}
