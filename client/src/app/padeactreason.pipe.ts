import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'paDeactReason'
})
export class PADeactReasonPipe implements PipeTransform {

  transform(value: number): string {
    if (value===1) return 'По инициативе пациента';
    if (value===2) return 'Не удалось связаться';
    if (value===3) return 'Нет специалиста в МО';
    if (value===4) return 'Записан в другую МО';
    if (value===5) return 'Услуга оказана до обработки';
    return '-';
  }

}
