package ru.tdi.misintegration.szpv.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class SpecTuple {
    String specId;
    String ferSpecId;
    String specName;
}
