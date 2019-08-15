import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'pastatus'
})
export class PastatusPipePipe implements PipeTransform {

  transform(value: number): string {
    if (value===1) return 'Активна';
    if (value===2) return 'Записан на прием';
    if (value===3) return 'Отменена';
    return 'Неизвестный';
  }

}
