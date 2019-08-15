import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'paclaim'
})
export class PaclaimPipe implements PipeTransform {

  transform(value: string): string {
    if (value==='1') return 'Нет свободных талонов';
    if (value==='2') return 'Нет удобного времени';
    if (value==='3') return 'Нет специалиста';
    return 'Неизвестно';
  }

}
