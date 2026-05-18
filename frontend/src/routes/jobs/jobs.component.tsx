import { useState } from 'react';
import { useJobs, useRunJob, useDeleteJob } from '@lib/hooks/jobs.hook';
import { ShButton } from '@lib/components/sh-button/button.component';
import {
  ShCard,
  ShCardContent,
  ShCardFooter,
  ShCardHeader,
  ShCardTitle,
  ShCardDescription,
} from '@lib/components/sh-card/card.component';
import {
  ShAlertDialog,
  ShAlertDialogAction,
  ShAlertDialogCancel,
  ShAlertDialogContent,
  ShAlertDialogDescription,
  ShAlertDialogFooter,
  ShAlertDialogHeader,
  ShAlertDialogTitle,
} from '@lib/components/sh-alert-dialog/alert-dialog.component';
import { ShSkeleton } from '@lib/components/sh-skeleton/skeleton.component';
import { ShBadge } from '@lib/components/sh-badge/badge.component';
import { ShSeparator } from '@lib/components/sh-separator/separator.component';
import {
  ShTooltip,
  ShTooltipContent,
  ShTooltipTrigger,
} from '@lib/components/sh-tooltip/tooltip.component';
import { CreateJobSheet } from './components/create-job.component';
import { EditJobSheet } from './components/edit-job.component';
import type { JobResponse, ScheduleType } from './jobs.type';
import {
  Play,
  Trash2,
  Plus,
  RefreshCw,
  BriefcaseBusiness,
  Pencil,
  ScrollText,
  Database,
  CalendarClock,
} from 'lucide-react';
import { cn } from '@lib/utils/cn.util';
import { toast } from 'sonner';
import { JobLogsSheet } from './components/job-logs.component';

const scheduleMeta: Record<
  ScheduleType,
  { label: string; variant: 'default' | 'secondary' | 'running' | 'success' | 'warning' }
> = {
  MANUAL: { label: 'Manual', variant: 'secondary' },
  INTERVAL: { label: 'Intervalo', variant: 'default' },
  CRON: { label: 'Cron', variant: 'warning' },
  CONTINUOUS: { label: 'Contínuo', variant: 'running' },
};

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );

export const JobsRoute = () => {
  const { data: jobs, isLoading, isError, refetch, isFetching } = useJobs();
  const runJob = useRunJob();
  const deleteJob = useDeleteJob();

  const [createOpen, setCreateOpen] = useState(false);
  const [editJob, setEditJob] = useState<JobResponse | null>(null);
  const [logsJob, setLogsJob] = useState<{ id: number; name: string } | null>(null);
  const [jobToDelete, setJobToDelete] = useState<number | null>(null);

  const handleRun = async (id: number) => {
    try {
      await runJob.mutateAsync(id);
      toast.success('Job enfileirado para execução');
    } catch {
      toast.error('Erro ao disparar job');
    }
  };

  const handleDelete = async () => {
    if (jobToDelete === null) return;
    try {
      await deleteJob.mutateAsync(jobToDelete);
      toast.success('Job removido');
    } catch {
      toast.error('Erro ao remover job');
    } finally {
      setJobToDelete(null);
    }
  };

  return (
    <>
      <div className="space-y-6">
        {/* Page header */}
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">Jobs</h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Gerencie e monitore suas migrações do SharePoint.
            </p>
          </div>
          <div className="flex gap-2">
            <ShTooltip>
              <ShTooltipTrigger asChild>
                <ShButton
                  variant="outline"
                  size="icon"
                  onClick={() => refetch()}
                  disabled={isFetching}
                >
                  <RefreshCw className={cn('w-4 h-4', isFetching && 'animate-spin')} />
                </ShButton>
              </ShTooltipTrigger>
              <ShTooltipContent>Atualizar</ShTooltipContent>
            </ShTooltip>
            <ShButton onClick={() => setCreateOpen(true)}>
              <Plus className="w-4 h-4 mr-2" />
              Novo Job
            </ShButton>
          </div>
        </div>

        {/* Loading skeletons */}
        {isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, index) => (
              <ShCard key={index}>
                <ShCardHeader>
                  <ShSkeleton className="h-5 w-40" />
                  <ShSkeleton className="h-4 w-28 mt-1" />
                </ShCardHeader>
                <ShCardContent className="space-y-3">
                  <ShSkeleton className="h-4 w-full" />
                  <ShSkeleton className="h-4 w-3/4" />
                </ShCardContent>
                <ShCardFooter className="border-t pt-3 gap-2">
                  <ShSkeleton className="h-8 w-20" />
                  <ShSkeleton className="h-8 w-20" />
                  <ShSkeleton className="h-8 w-8 ml-auto" />
                </ShCardFooter>
              </ShCard>
            ))}
          </div>
        )}

        {/* Error */}
        {isError && (
          <ShCard className="border-destructive/30">
            <ShCardContent className="py-12 text-center text-sm text-destructive">
              Erro ao carregar jobs. Verifique se o servidor está rodando.
            </ShCardContent>
          </ShCard>
        )}

        {/* Empty state */}
        {!isLoading && !isError && jobs?.length === 0 && (
          <ShCard className="border-dashed">
            <ShCardContent className="py-16 flex flex-col items-center gap-4 text-muted-foreground">
              <BriefcaseBusiness className="w-12 h-12 opacity-20" />
              <div className="text-center">
                <p className="font-medium text-foreground">Nenhum job cadastrado</p>
                <p className="text-sm mt-1">Crie seu primeiro job de migração para começar.</p>
              </div>
              <ShButton onClick={() => setCreateOpen(true)}>
                <Plus className="w-4 h-4 mr-2" />
                Criar primeiro job
              </ShButton>
            </ShCardContent>
          </ShCard>
        )}

        {/* Job cards grid */}
        {!isLoading && !isError && jobs && jobs.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {jobs.map((job) => {
              const schedule = scheduleMeta[job.scheduleType as ScheduleType];
              const fieldCount = job.migration
                ? Object.keys(job.migration.fieldMappings).length
                : 0;
              return (
                <ShCard key={job.id} className="flex flex-col">
                  <ShCardHeader className="pb-3">
                    <div className="flex items-start justify-between gap-2">
                      <ShCardTitle className="text-base leading-snug">{job.name}</ShCardTitle>
                      <ShBadge variant={schedule.variant} className="shrink-0">
                        {schedule.label}
                      </ShBadge>
                    </div>
                    <ShCardDescription className="flex items-center gap-1.5 mt-1">
                      <Database className="w-3.5 h-3.5 shrink-0" />
                      {job.targetDb} → {job.migration?.tableName || 'root'}
                    </ShCardDescription>
                  </ShCardHeader>

                  <ShCardContent className="pb-3 space-y-2 flex-1">
                    <ShSeparator />
                    <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground">
                      <div className="flex items-center gap-1.5">
                        <CalendarClock className="w-3.5 h-3.5" />
                        {job.scheduleType === 'INTERVAL' && job.intervalValue
                          ? `A cada ${job.intervalValue} ${job.intervalUnit}`
                          : job.scheduleType === 'CRON' && job.cronExpression
                            ? job.cronExpression
                            : schedule.label}
                      </div>
                      <div className="text-right">
                        {fieldCount} campo{fieldCount !== 1 ? 's' : ''} mapeado
                        {fieldCount !== 1 ? 's' : ''}
                      </div>
                    </div>
                    <div className="text-[11px] text-muted-foreground">
                      Criado em {formatDate(job.createdAt)}
                    </div>
                  </ShCardContent>

                  <ShCardFooter className="border-t pt-3 gap-1.5 flex-wrap">
                    <ShTooltip>
                      <ShTooltipTrigger asChild>
                        <ShButton
                          size="sm"
                          variant="outline"
                          onClick={() => handleRun(job.id)}
                          disabled={runJob.isPending}
                          className="gap-1.5"
                        >
                          <Play className="w-3.5 h-3.5 text-green-500" />
                          Rodar
                        </ShButton>
                      </ShTooltipTrigger>
                      <ShTooltipContent>Disparar execução agora</ShTooltipContent>
                    </ShTooltip>

                    <ShTooltip>
                      <ShTooltipTrigger asChild>
                        <ShButton
                          size="sm"
                          variant="outline"
                          onClick={() => setEditJob(job)}
                          className="gap-1.5"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                          Editar
                        </ShButton>
                      </ShTooltipTrigger>
                      <ShTooltipContent>Editar configurações</ShTooltipContent>
                    </ShTooltip>

                    <ShTooltip>
                      <ShTooltipTrigger asChild>
                        <ShButton
                          size="sm"
                          variant="outline"
                          onClick={() => setLogsJob({ id: job.id, name: job.name })}
                          className="gap-1.5"
                        >
                          <ScrollText className="w-3.5 h-3.5" />
                          Logs
                        </ShButton>
                      </ShTooltipTrigger>
                      <ShTooltipContent>Ver histórico de execuções</ShTooltipContent>
                    </ShTooltip>

                    <ShTooltip>
                      <ShTooltipTrigger asChild>
                        <ShButton
                          size="icon-sm"
                          variant="ghost"
                          className="ml-auto text-destructive hover:text-destructive"
                          onClick={() => setJobToDelete(job.id)}
                          disabled={deleteJob.isPending}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </ShButton>
                      </ShTooltipTrigger>
                      <ShTooltipContent>Remover job</ShTooltipContent>
                    </ShTooltip>
                  </ShCardFooter>
                </ShCard>
              );
            })}
          </div>
        )}
      </div>

      {/* Create dialog */}
      <CreateJobSheet open={createOpen} onOpenChange={setCreateOpen} />

      {/* Edit sheet */}
      <EditJobSheet
        job={editJob}
        open={editJob !== null}
        onOpenChange={(open) => !open && setEditJob(null)}
      />

      {/* Logs sheet */}
      <JobLogsSheet
        jobId={logsJob?.id ?? null}
        jobName={logsJob?.name ?? ''}
        open={logsJob !== null}
        onOpenChange={(open: boolean) => !open && setLogsJob(null)}
      />

      {/* Delete confirmation */}
      <ShAlertDialog
        open={jobToDelete !== null}
        onOpenChange={(open) => !open && setJobToDelete(null)}
      >
        <ShAlertDialogContent size="sm">
          <ShAlertDialogHeader>
            <ShAlertDialogTitle>Remover job?</ShAlertDialogTitle>
            <ShAlertDialogDescription>
              Esta ação não pode ser desfeita. O job será removido permanentemente junto com seu
              agendamento.
            </ShAlertDialogDescription>
          </ShAlertDialogHeader>
          <ShAlertDialogFooter>
            <ShAlertDialogCancel>Cancelar</ShAlertDialogCancel>
            <ShAlertDialogAction variant="destructive" onClick={handleDelete}>
              Remover
            </ShAlertDialogAction>
          </ShAlertDialogFooter>
        </ShAlertDialogContent>
      </ShAlertDialog>
    </>
  );
};
