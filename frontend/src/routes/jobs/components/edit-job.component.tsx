import { useState, useEffect } from 'react';
import { useUpdateJob } from '@lib/hooks/jobs.hook';
import { useConnections } from '@lib/hooks/connections.hook';
import type { JobNode, JobRequest, JobResponse, ScheduleType, TargetDb } from '../jobs.type';
import {
  ShSheet,
  ShSheetContent,
  ShSheetHeader,
  ShSheetBody,
  ShSheetFooter,
  ShSheetTitle,
  ShSheetDescription,
} from '@lib/components/sh-sheet/sheet.component';
import { ShTabs, ShTabsList, ShTabsTrigger, ShTabsContent } from '@lib/components/sh-tabs/tabs.component';
import { ShInput } from '@lib/components/sh-input/input.component';
import { ShLabel } from '@lib/components/sh-label/label.component';
import { ShSelect, ShSelectItem } from '@lib/components/sh-select/select.component';
import { ShButton } from '@lib/components/sh-button/button.component';
import { Loader2, Save } from 'lucide-react';
import { toast } from 'sonner';
import { MigrationNode } from './migration-node.component';

const TARGET_DBS: TargetDb[] = ['MYSQL', 'POSTGRESQL', 'MONGODB'];
const INTERVAL_UNITS = ['MINUTES', 'HOURS', 'DAYS'] as const;

interface EditJobSheetProps {
  job: JobResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const EditJobSheet = ({ job, open, onOpenChange }: EditJobSheetProps) => {
  const [name, setName] = useState('');
  const [targetDb, setTargetDb] = useState<TargetDb>('MYSQL');
  const [connectionKey, setConnectionKey] = useState('');
  const [pageSize, setPageSize] = useState(1000);
  const [scheduleType, setScheduleType] = useState<ScheduleType>('MANUAL');
  const [intervalValue, setIntervalValue] = useState(1);
  const [intervalUnit, setIntervalUnit] = useState<typeof INTERVAL_UNITS[number]>('HOURS');
  const [cronExpression, setCronExpression] = useState('');
  const [migration, setMigration] = useState<JobNode | null>(null);

  const updateJob = useUpdateJob(job?.id ?? 0);
  const { data: connections, isLoading: loadingConnections } = useConnections(open);

  useEffect(() => {
    if (connections && connections.length > 0 && connectionKey === '') {
      const firstKey = connections[0].key;
      setTimeout(() => setConnectionKey(firstKey), 0);
    }
  }, [connections, connectionKey]);

  useEffect(() => {
    if (!job) return;
    setTimeout(() => {
      setName(job.name);
      setTargetDb(job.targetDb);
      setConnectionKey(job.connectionKey);
      setPageSize(job.pageSize);
      setScheduleType(job.scheduleType);
      setIntervalValue(job.intervalValue ?? 1);
      setIntervalUnit((job.intervalUnit as typeof INTERVAL_UNITS[number]) ?? 'HOURS');
      setCronExpression(job.cronExpression ?? '');
      setMigration(job.migration);
    }, 0);
  }, [job]);

  const handleSave = async () => {
    if (!job || !migration) return;

    const payload: JobRequest = {
      name,
      targetDb,
      connectionKey,
      pageSize,
      scheduleType,
      migration,
      intervalValue,
      intervalUnit,
      cronExpression,
    };

    try {
      await updateJob.mutateAsync(payload);
      toast.success('Job atualizado com sucesso!');
      onOpenChange(false);
    } catch {
      toast.error('Erro ao atualizar job');
    }
  };

  const canSave =
    name.trim() !== '' &&
    connectionKey !== '' &&
    migration !== null &&
    migration.siteId !== '' &&
    migration.tableName !== '' &&
    (scheduleType !== 'CRON' || cronExpression.trim() !== '');

  return (
    <ShSheet open={open} onOpenChange={onOpenChange}>
      <ShSheetContent side="right" className="sm:max-w-7xl w-full flex flex-col p-0">
        <div className="px-6 py-4 border-b bg-muted/30 shrink-0">
          <ShSheetHeader>
            <ShSheetTitle>Editar Job de Migração</ShSheetTitle>
            <ShSheetDescription className="truncate">{job?.name}</ShSheetDescription>
          </ShSheetHeader>
        </div>

        <ShSheetBody className="p-0 flex-1 overflow-hidden">
          <ShTabs defaultValue="geral" className="flex flex-col h-full">
            <div className="px-6 pt-4 bg-background z-10">
              <ShTabsList>
                <ShTabsTrigger value="geral">Configuração Geral</ShTabsTrigger>
                <ShTabsTrigger value="migration">Árvore de Migração</ShTabsTrigger>
              </ShTabsList>
            </div>

            {/* ── Geral ── */}
            <ShTabsContent value="geral" className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
              <div className="space-y-1.5">
                <ShLabel>Nome do Job</ShLabel>
                <ShInput value={name} onChange={(e) => setName(e.target.value)} />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <ShLabel>Banco de Destino</ShLabel>
                  <ShSelect
                    value={targetDb}
                    onValueChange={(v) => setTargetDb(v as TargetDb)}
                  >
                    {TARGET_DBS.map((db) => (
                      <ShSelectItem key={db} value={db}>{db}</ShSelectItem>
                    ))}
                  </ShSelect>
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Conexão</ShLabel>
                  <ShSelect
                    value={connectionKey}
                    onValueChange={setConnectionKey}
                    placeholder={loadingConnections ? 'Carregando...' : 'Selecione...'}
                    disabled={loadingConnections}
                  >
                    {connections?.map((c) => (
                      <ShSelectItem key={c.key} value={c.key}>
                        {c.name} ({c.key})
                      </ShSelectItem>
                    ))}
                  </ShSelect>
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Page Size</ShLabel>
                  <ShInput
                    type="number"
                    value={pageSize}
                    onChange={(e) => setPageSize(parseInt(e.target.value) || 1000)}
                  />
                </div>
                
                <div className="space-y-1.5">
                  <ShLabel>Agendamento</ShLabel>
                  <ShSelect
                    value={scheduleType}
                    onValueChange={(v) => setScheduleType(v as ScheduleType)}
                  >
                    <ShSelectItem value="MANUAL">Manual</ShSelectItem>
                    <ShSelectItem value="INTERVAL">Intervalo</ShSelectItem>
                    <ShSelectItem value="CRON">Cron</ShSelectItem>
                    <ShSelectItem value="CONTINUOUS">Contínuo</ShSelectItem>
                  </ShSelect>
                </div>
              </div>

              {scheduleType === 'INTERVAL' && (
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <ShLabel>Valor</ShLabel>
                    <ShInput
                      type="number"
                      value={intervalValue}
                      onChange={(e) => setIntervalValue(parseInt(e.target.value) || 1)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <ShLabel>Unidade</ShLabel>
                    <ShSelect
                      value={intervalUnit}
                      onValueChange={(v) => setIntervalUnit(v as typeof INTERVAL_UNITS[number])}
                    >
                      {INTERVAL_UNITS.map((u) => (
                        <ShSelectItem key={u} value={u}>{u}</ShSelectItem>
                      ))}
                    </ShSelect>
                  </div>
                </div>
              )}

              {scheduleType === 'CRON' && (
                <div className="space-y-1.5">
                  <ShLabel>Expressão Cron</ShLabel>
                  <ShInput
                    placeholder="0 0 * * *"
                    value={cronExpression}
                    onChange={(e) => setCronExpression(e.target.value)}
                  />
                </div>
              )}
            </ShTabsContent>

            {/* ── Migration ── */}
            <ShTabsContent value="migration" className="flex-1 overflow-y-auto px-6 py-5">
              {migration && (
                <MigrationNode
                  node={migration}
                  targetDb={targetDb}
                  onChange={setMigration}
                  isRoot
                />
              )}
            </ShTabsContent>
          </ShTabs>
        </ShSheetBody>

        <div className="px-6 py-4 border-t bg-muted/30 shrink-0">
          <ShSheetFooter>
            <ShButton variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </ShButton>
            <ShButton onClick={handleSave} disabled={!canSave || updateJob.isPending}>
              {updateJob.isPending ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
              Salvar Alterações
            </ShButton>
          </ShSheetFooter>
        </div>
      </ShSheetContent>
    </ShSheet>
  );
};
