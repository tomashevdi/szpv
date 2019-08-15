package ru.tdi.misintegration.szpv.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.tdi.misintegration.szpv.ws.Doctor3;

@Data
@AllArgsConstructor
public class Doctor3WithSpec
{
    Doctor3 doctor;
    SpecTuple spec;
}
