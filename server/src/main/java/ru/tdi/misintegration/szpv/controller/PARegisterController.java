package ru.tdi.misintegration.szpv.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.tdi.misintegration.szpv.config.Lpu;
import ru.tdi.misintegration.szpv.model.DoctorRes;
import ru.tdi.misintegration.szpv.model.PARequestRes;
import ru.tdi.misintegration.szpv.model.SpecTuple;
import ru.tdi.misintegration.szpv.service.WaitingListRESTService;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/rfsz/webszp/")
public class PARegisterController {

    @Autowired
    WaitingListRESTService wtService;

    @RequestMapping(value = "reg/lpu", method = RequestMethod.GET)
    public List<Lpu> getRegLpus() {
        return wtService.getRegLpus();
    }

    @RequestMapping(value = "reg/spec", method = RequestMethod.GET)
    public List<SpecTuple> getRegSpec(@RequestParam Integer lpuId) {
        return wtService.getRegSpec(lpuId);
    }

    @RequestMapping(value = "reg/doct", method = RequestMethod.GET)
    public List<DoctorRes> getRegDocts(@RequestParam Integer lpuId, @RequestParam Integer specId) {
        return wtService.getRegDoct(lpuId,specId);
    }

    @RequestMapping(value = "reg/pat/{patId}", method = RequestMethod.GET)
    public String getRegPat(@PathVariable Integer patId) {
        return wtService.getPatientById(patId);
    }

    @RequestMapping(value = "reg/phone/{patId}", method = RequestMethod.GET)
    public String getRegPatPhone(@PathVariable Integer patId) {
        return wtService.getPatientPhone(patId);
    }


    @RequestMapping(value = "reg/par", method = RequestMethod.GET)
    public String getRegPat(@RequestParam Integer patId, @RequestParam Integer lpuId, @RequestParam Integer specId, @RequestParam Integer doctId, @RequestParam Integer reason, @RequestParam String info, @RequestParam String phone) {
        return wtService.registerPA(patId,lpuId,specId,doctId,reason,info,phone);
    }

    @RequestMapping(value = "reg/par/{idPat}", method = RequestMethod.GET)
    public List<PARequestRes> searchPARequests(@PathVariable Integer idPat) {
        return wtService.searchPARequests(idPat).stream().sorted( (p1,p2) -> {
            if (p1.getStatus()!=null && p2.getStatus()!=null ) {
                if (p1.getStatus()!=p2.getStatus() && (p1.getStatus()==1 || p2.getStatus()==1)) {
                     return p1.getStatus()-p2.getStatus();
                } else {
                    return p2.getCreated().compareTo(p1.getCreated());
                }
            }
            return 0;
        }).collect(Collectors.toList());
    }

    @RequestMapping(value = "reg/cancelPA/{idPar}", method = RequestMethod.GET)
    public Boolean cancelPA(@PathVariable String idPar) {
        return wtService.cancelPA(idPar,1,"Отмена ЦЗ");
    }


}
