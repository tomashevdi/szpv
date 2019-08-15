import {Component, OnInit, ViewChild} from '@angular/core';
import {PAService} from "./service/PAService.service";
import {PARequest} from "./domain/PARequest";
import 'rxjs/add/operator/map';

import '../../node_modules/primeng/resources/themes/omega/theme.css';
import '../../node_modules/primeng/resources/primeng.min.css';
import '../../node_modules/font-awesome/css/font-awesome.min.css';
import {Doctor} from "./domain/Doctor";
import {QueueItem} from './domain/QueueItem';
import {ConfirmationService, Message, SelectItem} from 'primeng/api';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  providers: [ConfirmationService]
})
export class AppComponent implements OnInit {

  title = 'app';

  msgs: Message[] = [];

  reqs: PARequest[];
  selectedPA: PARequest;
  doctors: Doctor[];
  selectedDoct: Doctor;
  selectedDate: Date;
  selectedQueue: QueueItem;
  queue: QueueItem[];
  busyDates: number[];
  month: number;

  dialCancel: boolean = false;
  cancelReasons: SelectItem[] = [
    {label: 'По инициативе пациента', value: 1},
    {label: 'Не удалось связаться', value: 2},
    {label: 'Нет специалиста в МО', value: 3},
    {label: 'Записан в другую МО', value: 4},
    {label: 'Услуга оказана до обработки', value: 5}
  ];
  cancelReason: number = 1;
  cancelComment: string = '';

  cols: any[] = [{header: 'Фамилия', field: 'patient.surname'}, {header: 'Врач', field: 'nameDoc'}];
  ru: any;

  @ViewChild('cal') calendar;

  dialHistory: boolean = false;
  historyPid: number  = null;

  specList: SelectItem[] = [];
  lpuList: SelectItem[] = [];

  constructor(private paServ: PAService, private confirmService: ConfirmationService) {
  }

  ngOnInit(): void {
    this.refreshPAList()
    this.ru = {
      firstDayOfWeek: 1,
      dayNames: ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'],
      dayNamesShort: ['Воск', 'Пон', 'Вт', 'Ср', 'Четв', 'Пят', 'Суб'],
      dayNamesMin: ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'],
      monthNames: ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'],
      monthNamesShort: ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек'],
      today: 'Сегодня',
      clear: 'Очистить'
    };
    this.selectedDate = new Date();
    this.month = this.selectedDate.getMonth()+1;
    this.busyDates = [];
  }

  refreshPAList(): void {
    this.reqs = [];
    this.paServ.getActivePARequests().then(value => {
        this.reqs = value;
        this.updateFilterLists();
      },
      reason => { this.msgs = []; this.msgs.push({severity: 'error', summary: 'Ошибка', detail: 'Возникла ошибка: ' + reason.message})});
  }

  paSelected() {
    this.busyDates = [];
    this.queue = [];
    this.doctors = [];
    this.selectedQueue = null;
    if (this.selectedPA != null)
      this.paServ.getDoctors(this.selectedPA.idSpec).then(value => this.doctors = value, reason => {  this.msgs = []; this.msgs.push({
        severity: 'error',
        summary: 'Ошибка',
        detail: 'Возникла ошибка: ' + reason.message
      })});
  }

  doctSelected() {
    this.queue = [];
    this.busyDates = [];
    this.selectedQueue = null;
    this.paServ.getBusyDates(this.selectedDoct.id, this.calendar.currentMonth + 1, this.calendar.currentYear).then(value => this.busyDates = value);
  }

  dateSelected() {
    this.queue = [];
    this.selectedQueue = null;
    this.paServ.getQueue(this.selectedDoct.id, this.selectedDate).then(value => this.queue = value);
  }


  monthChanged(event) {
    this.busyDates = [];
    this.month = event.month;
    this.paServ.getBusyDates(this.selectedDoct.id, event.month, event.year).then(value => this.busyDates = value);
  }

  cancelPAR() {
    this.paServ.cancelPA(this.selectedPA.idPar, this.cancelReason, this.cancelComment == null ? '' : this.cancelComment).then(value => {
      if (value == true) {
        this.msgs = [];
        this.msgs.push({severity: 'info', summary: 'Успешная отмена', detail: 'Заявка в ЖОС успешно отменена.'});
        this.selectedPA.status = 3;
      } else {
        this.msgs = [];
        this.msgs.push({severity: 'error', summary: 'Ошибка', detail: 'При отмена заявки возникла ошибка!'});
      }
    }, reason => {  this.msgs = []; this.msgs.push({
      severity: 'error',
      summary: 'Ошибка',
      detail: 'При отмена заявки возникла ошибка:' + reason.message
    })});
    this.dialCancel = false;
  }

  cancelQueue() {
    this.confirmService.confirm({
      message: 'Вы подтверждаете отмену записи?',
      accept: () => {
        this.paServ.cancelQueue(this.selectedQueue).then(value => {
          if (value == true) {
            this.msgs = [];
            this.msgs.push({severity: 'info', summary: 'Успешная отмена', detail: 'Запись успешно отменена.'});
            this.dateSelected();
          } else {
            this.msgs = [];
            this.msgs.push({severity: 'error', summary: 'Ошибка', detail: 'При отмене записи возникла ошибка!'});
          }
        }, reason => {this.msgs = []; this.msgs.push({
          severity: 'error',
          summary: 'Ошибка',
          detail: 'При отмена записи возникла ошибка:' + reason.message
        })});
      }
    });
  }

  setAppointment() {
    this.paServ.setAppointment(this.selectedPA.idPar, this.selectedPA.patient.idPat, this.selectedQueue).then(value => {
      if (value == true) {
        this.msgs = [];
        this.msgs.push({severity: 'info', summary: 'Успешная запись', detail: 'Записан успешно.'});
        this.selectedPA.status = 2;
        this.dateSelected();
      } else {
        this.msgs = [];
        this.msgs.push({severity: 'error', summary: 'Ошибка', detail: 'При записи возникла ошибка!'});
      }
    }, reason =>  {this.msgs = []; this.msgs.push({
      severity: 'error',
      summary: 'Ошибка',
      detail: 'При записи возникла ошибка:' + reason.message
    })});

  }

  searchPAR() {
    this.dialHistory = false;
    this.reqs = [];
    this.paServ.searchPARequests(this.historyPid).then(value => {
        this.reqs = value;
        this.updateFilterLists();
        if (this.reqs.length==0) {
          this.msgs = [];
          this.msgs.push({severity: 'info', summary: 'Результат', detail: 'Заявок не найдено.'});
        }
        console.log(this.reqs)
      },
      reason => { this.msgs = []; this.msgs.push({severity: 'error', summary: 'Ошибка', detail: 'Возникла ошибка: ' + reason.message})});

  }

  updateFilterLists() {
    this.updateSpecList();
    this.updateLpuList()
  }

  updateSpecList() {
    this.specList = [{label: 'Все', value: null}];
    this.reqs.map(value => value.nameSpec).filter((v,i,a)=>a.indexOf(v)==i).forEach(value => {
      this.specList.push({label: value, value: value});
    });

  }

  updateLpuList() {
    this.lpuList = [{label: 'Все', value: null}];
    this.reqs.map(value => value.lpuName).filter((v,i,a)=>a.indexOf(v)==i).forEach(value => {
      this.lpuList.push({label: value, value: value});
    });

  }


}
