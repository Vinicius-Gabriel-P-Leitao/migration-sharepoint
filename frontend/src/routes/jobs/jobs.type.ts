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

export interface JobNode {
  sharepointUrl?: string;
  siteId: string;
  listId: string;
  tableName: string;
  fieldMappings: Record<string, FieldMapping>;
  children: JobNode[];
}

export interface JobRequest {
  name: string;
  pageSize: number;
  targetDb: TargetDb;
  connectionKey: string;
  scheduleType: ScheduleType;
  intervalValue?: number;
  intervalUnit?: IntervalUnit;
  cronExpression?: string;
  migration: JobNode;
}

export interface JobResponse extends Omit<JobRequest, 'migration'> {
  id: number;
  createdAt: string;
  updatedAt: string;
  migration: JobNode;
}

export interface LogResponse {
  id: number;
  jobId: number;
  status: JobStatus;
  startedAt: string;
  finishedAt?: string;
  errorMessage?: string;
}
