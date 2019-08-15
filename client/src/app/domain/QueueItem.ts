export interface QueueItem {
  queueId : number;
  queueIdx : number;
  queueAction : number;
  time : string;
  clientName : string;
  free : boolean;
}
