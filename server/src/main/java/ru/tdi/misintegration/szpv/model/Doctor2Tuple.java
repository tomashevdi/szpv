package ru.tdi.misintegration.szpv.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.tdi.misintegration.szpv.ws.Doctor2;

import java.util.Date;

@Data
@AllArgsConstructor
public class Doctor2Tuple {
    Integer docId;
    Date dt;
}
