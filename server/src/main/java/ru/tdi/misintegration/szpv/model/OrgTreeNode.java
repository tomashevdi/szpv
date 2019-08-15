package ru.tdi.misintegration.szpv.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrgTreeNode {
    Integer id;
    String label;
    Boolean doctor = false;
    List<OrgTreeNode> children = new ArrayList<>();
}
