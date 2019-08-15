package ru.tdi.misintegration.szpv.model;

import lombok.Data;

@Data
public class QueueItem {
    Integer queueId;
    Integer queueIdx;
    Integer queueAction;
    String time;
    String clientName;
    Boolean free;
}
