import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { InputTextModule }  from 'primeng/inputtext';
import { ButtonModule }  from 'primeng/button';
import { TableModule }  from 'primeng/table';
import { DialogModule }  from 'primeng/dialog';
import { TreeModule} from "primeng/tree";
import {
  CalendarModule,
  ConfirmDialogModule,
  ContextMenuModule,
  DropdownModule
} from 'primeng/primeng';
import {ListboxModule} from "primeng/listbox";

import { AppComponent } from './app.component';
import {PAService} from "./service/PAService.service";
import { PastatusPipePipe } from './pastatus.pipe';
import { PasourcePipe } from './pasource.pipe';
import { PaclaimPipe } from './paclaim.pipe';
import {GrowlModule} from 'primeng/growl';
import {PADeactReasonPipe} from "./padeactreason.pipe";


@NgModule({
  declarations: [
    AppComponent,
    PastatusPipePipe,
    PasourcePipe,
    PaclaimPipe,
    PADeactReasonPipe
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    FormsModule,
    TableModule,
    HttpClientModule,
    InputTextModule,
    DialogModule,
    ButtonModule,
    TreeModule,
    CalendarModule,
    ListboxModule,
    DropdownModule,
    ContextMenuModule,
    GrowlModule,
    TableModule,
    ConfirmDialogModule
  ],
  providers: [PAService],
  bootstrap: [AppComponent]
})
export class AppModule { }
