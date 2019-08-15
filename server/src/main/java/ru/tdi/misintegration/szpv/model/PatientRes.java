package ru.tdi.misintegration.szpv.model;

import lombok.Data;

import java.util.Date;

@Data
public class PatientRes {
    String idPat;
    String surname;
    String name;
    String secondName;
    Date birthDate;
    String addInfo;
    String email;
    String phone;
}
