package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

@Component
@ConditionalOnProperty(value = "notification.scheduling.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationSender {

    @Autowired
    AppointmentService appService;

    @Scheduled(cron = "${notification.scheduling.appointments}")
    public void sendAppointmentNotifications() {
        Calendar cal2d = Calendar.getInstance();
        cal2d.add(Calendar.HOUR, -48);
        appService.sendAppointmentNotification(cal2d.getTime(), new Date());
    }

    @Scheduled(cron = "${notification.scheduling.appointmentsCancel}")
    public void sendAppointmentCancelNotifications() {
        Calendar cal2d = Calendar.getInstance();
        cal2d.add(Calendar.HOUR, -48);
        appService.sendCancelNotifications(cal2d.getTime(), new Date());
    }

    @Scheduled(cron = "${notification.scheduling.appointmentsDone}")
    public void sendAppointmentDoneNotifications() {
        Calendar calWeek = Calendar.getInstance();
        calWeek.add(Calendar.DAY_OF_MONTH, -7);
        appService.sendDoneNotifications(calWeek.getTime(), new Date());
    }

}
