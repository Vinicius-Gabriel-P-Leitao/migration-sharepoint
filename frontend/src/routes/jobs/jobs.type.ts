export type JobStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type ScheduleType = 'MANUAL' | 'INTERVAL' | 'CRON' | 'CONTINUOUS';
export type TargetDb = 'MYSQL' | 'POSTGRESQL' | 'MONGODB';
export type IntervalUnit = 'MINUTES' | 'HOURS' | 'DAYS';
export type CanonicalType = 'TEXT' | 'NUMBER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME';

export interface FieldMapping {
  column: string;
  type: CanonicalType;
  nativeType: string;
}

export interface JobRequest {
  name: string;
  siteId: string;
  listId: string;
  pageSize: number;
  fieldMappings: Record<string, FieldMapping>;
  targetDb: TargetDb;
  connectionKey: string;
  tableName: string;
  scheduleType: ScheduleType;
  intervalValue?: number;
  intervalUnit?: IntervalUnit;
  cronExpression?: string; 
}

export interface JobResponse extends JobRequest {
  id: number;
  createdAt: string;
  updatedAt: string;
}

export interface LogResponse {
  id: number;
  jobId: number;
  status: JobStatus;
  startedAt: string;
  finishedAt?: string;
  errorMessage?: string;
}
