import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {PARequest} from '../domain/PARequest';
import {TreeNode} from 'primeng/api';
import {Doctor} from '../domain/Doctor';
import {DatePipe} from '@angular/common';
import {QueueItem} from '../domain/QueueItem';
import {RequestOptions} from '@angular/http';

@Injectable()
export class PAService {

  srv : string = "http://192.168.106.2:7744/rfsz/webszp/";
  userId: number = 899;

  constructor(private http: HttpClient) { }

  getActivePARequests() {
    return this.http.get<PARequest[]>(this.srv+'parequest').toPromise();
  }

  getDoctors(specId: string) {
    return this.http.get<Doctor[]>(this.srv+'doctorList/' + specId).toPromise();
  }

  getQueue(docId: number, dt: Date) {
    const pipe = new DatePipe('en-US');
    return this.http.get<QueueItem[]>(this.srv+'queueList/' + docId + '/' + pipe.transform(dt, 'dd-MM-yyyy')).toPromise();
  }

  getBusyDates(docId: number, month : number, year : number) {
    return this.http.get<number[]>(this.srv+'getFreeDaysInMonth/' + docId + '/' + month+ '/' + year).toPromise();
  }

  cancelPA(idPar: string, reason: number, comment: string) {
    let params = new HttpParams().set('reason', reason.toString()).set('comment', comment);
    return this.http.get(this.srv+'cancelPA/' + idPar, {params} ).toPromise();
  }

  cancelQueue(queue : QueueItem) {
    let params = new HttpParams().set('idUser',this.userId.toString());
    return this.http.post(this.srv+'cancelQueue',  queue, {params} ).toPromise();
  }

  setAppointment(idPar : string, idPat: number, queue : QueueItem) {
    let params = new HttpParams().set('idPat', idPat.toString()).set('idPar', idPar).set('idUser',this.userId.toString());
    return this.http.post(this.srv+'setAppointment',  queue, {params} ).toPromise();
  }

  searchPARequests(idPat: number) {
    return this.http.get<PARequest[]>(this.srv+'parequest/'+idPat).toPromise();
  }


}
