import { useQueries } from '@tanstack/react-query';
import { useJobs } from '@lib/hooks/jobs.hook';
import { jobsService } from '@lib/services/jobs.service';
import type { JobStatus, LogResponse } from '@routes/jobs/jobs.type';
import { ShBadge } from '@lib/components/sh-badge/badge.component';
import { ShSkeleton } from '@lib/components/sh-skeleton/skeleton.component';
import { ShCard, ShCardContent } from '@lib/components/sh-card/card.component';
import { ScrollText } from 'lucide-react';

const statusMeta: Record<
  JobStatus,
  { label: string; variant: 'default' | 'secondary' | 'running' | 'success' | 'warning' | 'destructive' }
> = {
  RUNNING: { label: 'Rodando', variant: 'running' },
  SUCCESS: { label: 'Sucesso', variant: 'success' },
  FAILED: { label: 'Falha', variant: 'destructive' },
};

const formatDateTime = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso));

const formatDuration = (start: string, end?: string) => {
  if (!end) return '—';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
};

interface LogWithJobName extends LogResponse {
  jobName: string;
}

export const LogsRoute = () => {
  const { data: jobs, isLoading: loadingJobs } = useJobs();

  const logQueries = useQueries({
    queries: (jobs ?? []).map((job) => ({
      queryKey: ['jobs', job.id, 'logs'],
      queryFn: () => jobsService.getLogs(job.id),
      enabled: !!jobs,
    })),
  });

  const isLoading = loadingJobs || logQueries.some((q) => q.isLoading);

  const allLogs: LogWithJobName[] = logQueries
    .flatMap((q, i) =>
      ((q.data as LogResponse[]) ?? []).map((log) => ({
        ...log,
        jobName: jobs?.[i]?.name ?? `Job ${log.jobId}`,
      }))
    )
    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Logs</h2>
        <p className="text-sm text-muted-foreground mt-0.5">
          Histórico global de execuções de todos os jobs.
        </p>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="rounded-lg border p-4 space-y-2">
              <div className="flex items-center justify-between">
                <ShSkeleton className="h-4 w-40" />
                <ShSkeleton className="h-5 w-16" />
              </div>
              <ShSkeleton className="h-3 w-56" />
              <ShSkeleton className="h-3 w-32" />
            </div>
          ))}
        </div>
      )}

      {!isLoading && allLogs.length === 0 && (
        <ShCard className="border-dashed">
          <ShCardContent className="py-16 flex flex-col items-center gap-4 text-muted-foreground">
            <ScrollText className="w-12 h-12 opacity-20" />
            <div className="text-center">
              <p className="font-medium text-foreground">Nenhuma execução registrada</p>
              <p className="text-sm mt-1">Execute um job para ver o histórico aqui.</p>
            </div>
          </ShCardContent>
        </ShCard>
      )}

      {!isLoading && allLogs.length > 0 && (
        <div className="space-y-2">
          {allLogs.map((log) => {
            const meta = statusMeta[log.status];
            return (
              <div key={log.id} className="rounded-lg border p-4 space-y-1.5 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium truncate">{log.jobName}</span>
                  <ShBadge variant={meta.variant} className="shrink-0">
                    {meta.label}
                  </ShBadge>
                </div>
                <div className="grid grid-cols-2 gap-x-4 text-xs text-muted-foreground">
                  <span>Início: {formatDateTime(log.startedAt)}</span>
                  <span>Duração: {formatDuration(log.startedAt, log.finishedAt)}</span>
                  {log.finishedAt && <span>Fim: {formatDateTime(log.finishedAt)}</span>}
                </div>
                {log.errorMessage && (
                  <div className="mt-1.5 rounded bg-destructive/10 px-2.5 py-2 text-xs text-destructive font-mono whitespace-pre-wrap break-all">
                    {log.errorMessage}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
