package ru.tdi.misintegration.szpv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Lazy
public class WaitingListAsyncService {

    @Autowired
    WaitingListRESTService waitingListRESTService;

    @Async
    public void removePARfromCache(String idPar) {
        waitingListRESTService.getActivePARequests().removeIf(par->par.getIdPar().equals(idPar));
    }

}
