package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotifyAsyncService {

    @Autowired
    AppointmentService appService;

    @Async
    public void sendAppointmentNotificationAsync(Integer queueId) {
        appService.sendAppointmentNotification(queueId);
    }


}
