import {Patient} from "./Patient";

export interface PARequest {
  idLpu?: number,
  lpuName?: string,
  idPar?: string,
  created?: Date,
  idDoc?: string,
  nameDoc?: string,
  idSpec?: string,
  nameSpec?: string,
  claim?: string,
  info?: string,
  period?: string;
  startDate?: Date,
  endDate?: Date,
  source?: number,
  status?: number,
  patient : Patient,
  deactivateDate?: Date,
  deactivateReason?: number,
  deactivateComment?: string,
  invalidClient?: boolean
}
