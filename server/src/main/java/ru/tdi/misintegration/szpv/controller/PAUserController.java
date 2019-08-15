package ru.tdi.misintegration.szpv.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.tdi.misintegration.szpv.config.Lpu;
import ru.tdi.misintegration.szpv.model.DoctorRes;
import ru.tdi.misintegration.szpv.model.PARequestRes;
import ru.tdi.misintegration.szpv.model.QueueItem;
import ru.tdi.misintegration.szpv.model.SpecTuple;
import ru.tdi.misintegration.szpv.service.WaitingListRESTService;

import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/rfsz/webszp/")
public class PAUserController {

    @Autowired
    WaitingListRESTService wtService;

    @RequestMapping("parequest")
    public List<PARequestRes> getActivePARequests() {
        return wtService.getActivePARequests();
    }

    @RequestMapping("parequest/{idPat}")
    public List<PARequestRes> searchPARequests(@PathVariable Integer idPat) {
        return wtService.searchPARequests(idPat);
    }


    @RequestMapping("doctorList/{id}")
    public List<DoctorRes> getLpuTree(@PathVariable String id) {
        return wtService.getDoctors(id);
    }

    @RequestMapping("queueList/{docId}/{dt}")
    public List<QueueItem> getQueueList(@PathVariable Integer docId, @PathVariable String dt) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        return wtService.getQueue(docId, sdf.parse(dt));
    }

    @RequestMapping("getFreeDaysInMonth/{docId}/{month}/{year}")
    public List<Integer> getFreeDaysInMonth(@PathVariable Integer docId, @PathVariable Integer month, @PathVariable Integer year) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        LocalDate begDt = LocalDate.of( year,month,1);
        LocalDate endDt = LocalDate.of(year,month, begDt.lengthOfMonth());

        List<Date> dts = wtService.getAvailableDates(String.valueOf(docId),Date.from(begDt.atStartOfDay(ZoneId.of("UTC")).toInstant()),Date.from(endDt.atStartOfDay(ZoneId.of("UTC")).toInstant()));

        return dts.stream().map(date -> date.getDate()).collect(Collectors.toList());
    }

    @RequestMapping(value = "cancelPA/{idPar}", method = RequestMethod.GET)
    public Boolean cancelPA(@PathVariable String idPar, @RequestParam  Integer reason, @RequestParam  String comment) {
        return wtService.cancelPA(idPar,reason,comment);
    }

    @RequestMapping(value = "cancelQueue", method = RequestMethod.POST)
    public Boolean cancelQueue(@RequestBody QueueItem queueItem, @RequestParam Integer idUser) {
        return wtService.cancelQueue(queueItem, idUser);
    }

    @RequestMapping(value = "setAppointment", method = RequestMethod.POST)
    public Boolean setAppointment(@RequestBody QueueItem queueItem, @RequestParam String idPar, @RequestParam Integer idPat, @RequestParam Integer idUser) {
        return wtService.setAppointment(idPar, idPat, queueItem, idUser);
    }


    @RequestMapping(value= "/**", method=RequestMethod.OPTIONS)
    public void corsHeaders(HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, x-requested-with");
        response.addHeader("Access-Control-Max-Age", "3600");
    }

}
