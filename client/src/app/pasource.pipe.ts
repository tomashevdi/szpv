import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'pasource'
})
export class PasourcePipe implements PipeTransform {

  transform(value: number): string {
    if (value===1) return 'ЦТО';
    if (value===2) return 'Инфомат';
    if (value===3) return 'Регистратура';
    if (value===4) return 'АРМ врача';
    if (value===5) return 'Интернет';
    if (value===6) return 'Прочее';
    return 'Неопределено';
  }

}
