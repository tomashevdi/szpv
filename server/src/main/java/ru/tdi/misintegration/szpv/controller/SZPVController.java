package ru.tdi.misintegration.szpv.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import ru.tdi.misintegration.szpv.service.*;
import ru.tdi.misintegration.szpv.ws.*;
import ru.tdi.misintegration.szpv.Utils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Endpoint
public class SZPVController {

    @Autowired
    QueueInformationService queueServ;


    @Autowired
    PatientService ptService;

    @Autowired
    AppointmentService appService;

    @Autowired
    UserService userService;

    @Autowired
    WaitingListService wlService;

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetSpesialityList")
    @ResponsePayload
    public GetSpesialityListResponse getSpecList(@RequestPayload GetSpesialityList request) {
        GetSpesialityListResponse r = new GetSpesialityListResponse();
        GetSpesialityListResult rs = new GetSpesialityListResult();
        ArrayOfSpesiality specs = new ArrayOfSpesiality();
        rs.setListSpesiality(specs);
        r.setGetSpesialityListResult(rs);
        rs.setSuccess(true);

        specs.getSpesiality().addAll(queueServ.getSpecialityList(request.getIdLpu()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetDoctorList")
    @ResponsePayload
    public GetDoctorListResponse getDoctList(@RequestPayload GetDoctorList request) {
        GetDoctorListResponse r = new GetDoctorListResponse();
        GetDoctorListResult rs = new GetDoctorListResult();
        ArrayOfDoctor docs = new ArrayOfDoctor();
        rs.setDocs(docs);
        r.setGetDoctorListResult(rs);

        rs.setSuccess(true);

        docs.getDoctor().addAll(queueServ.getDoctorList(request.getIdLpu(),request.getIdSpesiality()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetAvailableDates")
    @ResponsePayload
    public GetAvailableDatesResponse getAvailableDates(@RequestPayload GetAvailableDates request) {
        GetAvailableDatesResponse r = new GetAvailableDatesResponse();
        GetAvailableDatesResult rs = new GetAvailableDatesResult();
        ArrayOfdateTime dt = new ArrayOfdateTime();
        rs.setAvailableDateList(dt);
        r.setGetAvailableDatesResult(rs);

        List<Date> dts = queueServ.getAvailableDates(request.getIdDoc(),Utils.fromXMLDate(request.getVisitStart()),Utils.fromXMLDate(request.getVisitEnd()));

        rs.setSuccess(true);
        dt.getDateTime().addAll(dts.stream().map(date -> Utils.toXMLDate(date)).collect(Collectors.toList()));

        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetAvaibleAppointments")
    @ResponsePayload
    public GetAvaibleAppointmentsResponse getAvailableAppointments(@RequestPayload GetAvaibleAppointments request) {
        GetAvaibleAppointmentsResponse r  =new GetAvaibleAppointmentsResponse();
        GetAvaibleAppointmentsResult rs  = new GetAvaibleAppointmentsResult();
        ArrayOfAppointment apps = new ArrayOfAppointment();
        rs.setListAppointments(apps);
        r.setGetAvaibleAppointmentsResult(rs);

        rs.setSuccess(true);
        apps.getAppointment().addAll(queueServ.getAvailableAppointments(request.getIdDoc(),Utils.fromXMLDate(request.getVisitStart()),Utils.fromXMLDate(request.getVisitEnd())));

        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetWorkingTime")
    @ResponsePayload
    public GetWorkingTimeResponse getWorkingTime(@RequestPayload GetWorkingTime request) {
        GetWorkingTimeResponse r = new GetWorkingTimeResponse();
        GetWorkingTimeResult rs = new GetWorkingTimeResult();
        ArrayOfWorkingTime wt  = new ArrayOfWorkingTime();
        rs.setWorkingTimeList(wt);
        r.setGetWorkingTimeResult(rs);

        rs.setSuccess(true);

        wt.getWorkingTime().addAll(queueServ.getWorkingTime(request.getIdDoc(),Utils.fromXMLDate(request.getVisitStart()),Utils.fromXMLDate(request.getVisitEnd())));

        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetDocListFullTree")
    @ResponsePayload
    public GetDocListFullTreeResponse getDocListFullTree(@RequestPayload GetDocListFullTree request) {
        GetDocListFullTreeResponse r = new GetDocListFullTreeResponse();
        GetDocListFullTreeResult rs = new GetDocListFullTreeResult();
        ArrayOfEssence essence = new ArrayOfEssence();
        r.setGetDocListFullTreeResult(rs);
        rs.setListDoctor(essence);

        essence.getEssence().addAll(queueServ.getDocListFullTree(request.getIdLpu()));
        rs.setSuccess(true);

        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetDistrictList")
    @ResponsePayload
    public GetDistrictListResponse getDistrictList(@RequestPayload GetDistrictList request) {
        GetDistrictListResponse r = new GetDistrictListResponse();
        DistrictResult rs  = new DistrictResult();
        ArrayOfDistrict district = new ArrayOfDistrict();
        r.setGetDistrictListResult(rs);
        rs.setDistricts(district);
        rs.setSuccess(true);
        district.getDistrict().add(queueServ.getDistrict());
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetLPUList")
    @ResponsePayload
    public GetLPUListResponse getLPUList(@RequestPayload GetLPUList request) {
        GetLPUListResponse r = new GetLPUListResponse();
        GetLPUListResult rs = new GetLPUListResult();
        ArrayOfClinic lpus = new ArrayOfClinic();
        r.setGetLPUListResult(rs);
        rs.setListLPU(lpus);
        lpus.getClinic().addAll(queueServ.getLPUs());
        rs.setSuccess(true);
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "SearchTop10Patient")
    @ResponsePayload
    public SearchTop10PatientResponse searchTop10Patient(@RequestPayload SearchTop10Patient request) {
        SearchTop10PatientResponse r = new SearchTop10PatientResponse();
        SearchTop10PatientResult rs = new SearchTop10PatientResult();
        ArrayOfPatient pts = new ArrayOfPatient();
        r.setSearchTop10PatientResult(rs);
        rs.setListPatient(pts);
        rs.setSuccess(true);

        pts.getPatient().addAll(ptService.searchPatients(request.getPat()));

        return r;
    }

    //TODO Different checks (EGPU requirements)
    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "CheckPatient")
    @ResponsePayload
    public CheckPatientResponse checkPatient(@RequestPayload CheckPatient request) {
        CheckPatientResponse r  = new CheckPatientResponse();
        CheckPatientResult rs  = new CheckPatientResult();
        r.setCheckPatientResult(rs);
        rs.setIdPat(ptService.checkPatient(request.getPat()));
        rs.setSuccess(true);
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetPatientsAreas")
    @ResponsePayload
    public GetPatientsAreasResponse getPatientsAreas(@RequestPayload GetPatientsAreas request) {
        GetPatientsAreasResponse r = new GetPatientsAreasResponse();
        GetPatientsAreasResult rs = new GetPatientsAreasResult();
        ArrayOfPatientsArea pa = new ArrayOfPatientsArea();
        r.setGetPatientsAreasResult(rs);
        rs.setPatientsAreaList(pa);

        List<PatientsArea> areas = queueServ.getPatientsAreas(request.getIdLpu(), request.getIdPat());

        if (areas.isEmpty()) {
            rs.setErrorList(Utils.setError(41, "Отсутствует информация о врачебных участках по пациенту"));
            rs.setSuccess(false);
            return r;
        } else {
            pa.getPatientsArea().addAll(areas);
            rs.setSuccess(true);
        }
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetAvailableDoctors")
    @ResponsePayload
    public GetAvailableDoctorsResponse getAvailableDoctors(@RequestPayload GetAvailableDoctors request) {
        GetAvailableDoctorsResponse r = new GetAvailableDoctorsResponse();
        GetAvailableDoctorsResult rs = new GetAvailableDoctorsResult();
        ArrayOfSpeciality3 spec = new ArrayOfSpeciality3();
        r.setGetAvailableDoctorsResult(rs);
        rs.setListSpeciality(spec);
        rs.setSuccess(true);
        spec.getSpeciality3().addAll(queueServ.getAvailableDoctors(request.getIdLpu(), request.getIdPat(), request.getIdSpeciality(), request.getFerIdSpeciality()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "SetAppointment")
    @ResponsePayload
    public SetAppointmentResponse setAppointment(@RequestPayload SetAppointment request) {
        SetAppointmentResponse r = new SetAppointmentResponse();
        r.setSetAppointmentResult(appService.setAppointment(request));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "AddNewPatient")
    @ResponsePayload
    public AddNewPatientResponse addNewPatient(@RequestPayload AddNewPatient request) {
        AddNewPatientResponse r = new AddNewPatientResponse();
        AddNewPatientResult rs = new AddNewPatientResult();
        r.setAddNewPatientResult(rs);

        Optional<String> pat = ptService.addNewPatient(request.getPatient(), request.getGuid());

        if (pat.isPresent()) {
            rs.setIdPat(pat.get());
            rs.setSuccess(true);
        } else {
            rs.setErrorList(Utils.setError(99, "Ошибка при добавлении пациента"));
            rs.setSuccess(false);
        }
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "CreateClaimForRefusal")
    @ResponsePayload
    public CreateClaimForRefusalResponse createClaimForRefusal(@RequestPayload CreateClaimForRefusal request) {
        CreateClaimForRefusalResponse r = new CreateClaimForRefusalResponse();
        r.setCreateClaimForRefusalResult(appService.createClaimForRefusal(request.getIdPat(), request.getIdAppointment()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "UpdatePhoneByIdPat")
    @ResponsePayload
    public UpdatePhoneByIdPatResponse updatePhoneByIdPat(@RequestPayload UpdatePhoneByIdPat request) {
        UpdatePhoneByIdPatResponse r = new UpdatePhoneByIdPatResponse();
        r.setUpdatePhoneByIdPatResult(ptService.updatePhoneByIdPat(request.getIdPat(),request.getHomePhone(),request.getCellPhone(),request.getGuid()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetPatientHistory")
    @ResponsePayload
    public GetPatientHistoryResponse getPatientHistory(@RequestPayload GetPatientHistory request) {
        GetPatientHistoryResponse r = new GetPatientHistoryResponse();
        r.setGetPatientHistoryResult(ptService.getPatientHistory(request.getIdPat()));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "SetWaitingList")
    @ResponsePayload
    public SetWaitingListResponse setWaitingList(@RequestPayload SetWaitingList request) {
        SetWaitingListResponse r  = new SetWaitingListResponse();
        r.setSetWaitingListResult(wlService.setWaitingList(request));
        return r;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "GetAvailableAppointmentsByPARequest")
    @ResponsePayload
    public GetAvailableAppointmentsByPARequestResponse getAvailableAppointmentsByPARequest(@RequestPayload GetAvailableAppointmentsByPARequest request) {
        GetAvailableAppointmentsByPARequestResponse resp = new GetAvailableAppointmentsByPARequestResponse();
        GetAvailableAppointmentsByPARequestResult res = new GetAvailableAppointmentsByPARequestResult();
        resp.setGetAvailableAppointmentsByPARequestResult(res);

        res.setIdLpu(request.getIdLpu());
        res.setIdPat(request.getAttachedPARequest().getPARequestPatient().getIdPatient());
        ArrayOfSpeciality2 arrSpec = new ArrayOfSpeciality2();
        res.setListSpeciality(arrSpec);
        arrSpec.getSpeciality2().add(queueServ.getAvailableAppointmentsPA(request.getIdLpu(),request.getAttachedPARequest().getPARequest().getIdSpeciality(),request.getAttachedPARequest().getPARequestPatient().getIdPatient()));

        res.setSuccess(true);

        return resp;
    }


    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "SetAppointmentByPARequest")
    @ResponsePayload
    public SetAppointmentByPARequestResponse setAppointmentByPARequest(@RequestPayload SetAppointmentByPARequest request) {
        SetAppointmentByPARequestResponse resp = new SetAppointmentByPARequestResponse();
        SetAppointmentByPARequestResult res = new SetAppointmentByPARequestResult();
        resp.setSetAppointmentByPARequestResult(appService.setAppointmentByPARequest(request));
        return resp;
    }

    @PayloadRoot(namespace = "http://tempuri.org/", localPart = "InspectDoctorsReferral2")
    @ResponsePayload
    public InspectDoctorsReferral2Response inspectDoctorsReferral2(@RequestPayload InspectDoctorsReferral2 request) {
        InspectDoctorsReferral2Response resp = new InspectDoctorsReferral2Response();
        resp.setInspectDoctorsReferral2Result(queueServ.inspectDoctorsReferral2(request));
        return resp;
    }

}
