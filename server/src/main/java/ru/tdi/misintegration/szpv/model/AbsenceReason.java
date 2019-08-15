package ru.tdi.misintegration.szpv.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AbsenceReason {
    String reason;
    Integer group;
    LocalDate dt;
    LocalDate dtNext1;
    LocalDate dtNext2;
}
