import { useState, useEffect } from 'react';
import { useCreateJob } from '@lib/hooks/jobs.hook';
import { useConnections } from '@lib/hooks/connections.hook';
import type { JobNode, JobRequest, ScheduleType, TargetDb } from '../jobs.type';
import {
  ShSheet,
  ShSheetContent,
  ShSheetHeader,
  ShSheetTitle,
  ShSheetBody,
  ShSheetFooter,
} from '@lib/components/sh-sheet/sheet.component';
import {
  ShAlertDialog,
  ShAlertDialogContent,
  ShAlertDialogHeader,
  ShAlertDialogTitle,
  ShAlertDialogDescription,
  ShAlertDialogFooter,
  ShAlertDialogAction,
  ShAlertDialogCancel,
} from '@lib/components/sh-alert-dialog/alert-dialog.component';
import { ShInput } from '@lib/components/sh-input/input.component';
import { ShLabel } from '@lib/components/sh-label/label.component';
import { ShSelect, ShSelectItem } from '@lib/components/sh-select/select.component';
import { ShButton } from '@lib/components/sh-button/button.component';
import { ShSeparator } from '@lib/components/sh-separator/separator.component';
import { Loader2, AlertCircle, Save, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';
import { MigrationNode } from './migration-node.component';

const TARGET_DBS: TargetDb[] = ['MYSQL', 'POSTGRESQL', 'MONGODB'];
const INTERVAL_UNITS = ['MINUTES', 'HOURS', 'DAYS'] as const;

interface JobConfig {
  name: string;
  targetDb: TargetDb;
  connectionKey: string;
  pageSize: number;
  scheduleType: ScheduleType;
  intervalValue: number;
  intervalUnit: (typeof INTERVAL_UNITS)[number];
  cronExpression: string;
}

const defaultConfig: JobConfig = {
  name: '',
  targetDb: 'MYSQL',
  connectionKey: '',
  pageSize: 1000,
  scheduleType: 'MANUAL',
  intervalValue: 1,
  intervalUnit: 'HOURS',
  cronExpression: '',
};

const initialMigration: JobNode = {
  siteId: '',
  listId: '',
  tableName: '',
  fieldMappings: {},
  customFields: {},
  foreignKeys: [],
  children: [],
};

interface CreateJobSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const CreateJobSheet = ({ open, onOpenChange }: CreateJobSheetProps) => {
  const [config, setConfig] = useState<JobConfig>(defaultConfig);
  const [migration, setMigration] = useState<JobNode>(initialMigration);
  const [showConfirm, setShowConfirm] = useState(false);

  const createJob = useCreateJob();
  const { data: connections, isLoading: loadingConnections } = useConnections(open);

  useEffect(() => {
    if (connections && connections.length > 0 && config.connectionKey === '') {
      const firstKey = connections[0].key;
      setTimeout(() => {
        setConfig((previous) =>
          previous.connectionKey === '' ? { ...previous, connectionKey: firstKey } : previous,
        );
      }, 0);
    }
  }, [connections, config.connectionKey]);

  const handleSubmit = async () => {
    const payload: JobRequest = {
      ...config,
      migration,
    };

    try {
      await createJob.mutateAsync(payload);
      toast.success('Job criado com sucesso!');
      setShowConfirm(false);
      handleClose();
    } catch {
      toast.error('Erro ao criar job');
    }
  };

  const handleClose = () => {
    onOpenChange(false);
    setTimeout(() => {
      setConfig(defaultConfig);
      setMigration(initialMigration);
    }, 300);
  };

  const canSubmit =
    config.name.trim() !== '' &&
    config.connectionKey !== '' &&
    migration.siteId !== '' &&
    migration.tableName !== '' &&
    (config.scheduleType !== 'CRON' || config.cronExpression.trim() !== '');

  return (
    <>
      <ShSheet open={open} onOpenChange={handleClose}>
        <ShSheetContent side="right" className="sm:max-w-screen w-full flex flex-col p-0">
          <div className="px-6 py-4 border-b shrink-0 bg-muted/30">
            <ShSheetHeader>
              <ShSheetTitle className="text-xl">Novo Job de Migração</ShSheetTitle>
            </ShSheetHeader>
          </div>

          <ShSheetBody className="flex-1 min-h-0 overflow-y-auto p-6 space-y-8">
            {/* ── Configuração Global ── */}
            <section className="space-y-4">
              <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-2">
                <AlertCircle className="w-4 h-4" />
                Configuração Geral
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="col-span-1 md:col-span-2 space-y-1.5">
                  <ShLabel>Nome do Job</ShLabel>
                  <ShInput
                    placeholder="Ex: Migração Estrutural SharePoint"
                    value={config.name}
                    onChange={(event) =>
                      setConfig((previous) => ({ ...previous, name: event.target.value }))
                    }
                  />
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Banco de Destino</ShLabel>
                  <ShSelect
                    value={config.targetDb}
                    onValueChange={(value) =>
                      setConfig((previous) => ({ ...previous, targetDb: value as TargetDb }))
                    }
                  >
                    {TARGET_DBS.map((db) => (
                      <ShSelectItem key={db} value={db}>
                        {db}
                      </ShSelectItem>
                    ))}
                  </ShSelect>
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Conexão</ShLabel>
                  <ShSelect
                    value={config.connectionKey}
                    onValueChange={(value) =>
                      setConfig((previous) => ({ ...previous, connectionKey: value }))
                    }
                    placeholder={loadingConnections ? 'Carregando...' : 'Selecione...'}
                    disabled={loadingConnections}
                  >
                    {connections?.map((connection) => (
                      <ShSelectItem key={connection.key} value={connection.key}>
                        {connection.name} ({connection.key})
                      </ShSelectItem>
                    ))}
                  </ShSelect>
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Page Size</ShLabel>
                  <ShInput
                    type="number"
                    value={config.pageSize}
                    onChange={(event) =>
                      setConfig((previous) => ({
                        ...previous,
                        pageSize: parseInt(event.target.value) || 1000,
                      }))
                    }
                  />
                </div>

                <div className="space-y-1.5">
                  <ShLabel>Agendamento</ShLabel>
                  <ShSelect
                    value={config.scheduleType}
                    onValueChange={(value) =>
                      setConfig((previous) => ({
                        ...previous,
                        scheduleType: value as ScheduleType,
                      }))
                    }
                  >
                    <ShSelectItem value="MANUAL">Manual</ShSelectItem>
                    <ShSelectItem value="INTERVAL">Intervalo</ShSelectItem>
                    <ShSelectItem value="CRON">Cron</ShSelectItem>
                    <ShSelectItem value="CONTINUOUS">Contínuo</ShSelectItem>
                  </ShSelect>
                </div>

                {config.scheduleType === 'INTERVAL' && (
                  <>
                    <div className="space-y-1.5">
                      <ShLabel>Valor</ShLabel>
                      <ShInput
                        type="number"
                        value={config.intervalValue}
                        onChange={(event) =>
                          setConfig((previous) => ({
                            ...previous,
                            intervalValue: parseInt(event.target.value) || 1,
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1.5">
                      <ShLabel>Unidade</ShLabel>
                      <ShSelect
                        value={config.intervalUnit}
                        onValueChange={(value) =>
                          setConfig((previous) => ({
                            ...previous,
                            intervalUnit: value as (typeof INTERVAL_UNITS)[number],
                          }))
                        }
                      >
                        {INTERVAL_UNITS.map((unit) => (
                          <ShSelectItem key={unit} value={unit}>
                            {unit}
                          </ShSelectItem>
                        ))}
                      </ShSelect>
                    </div>
                  </>
                )}

                {config.scheduleType === 'CRON' && (
                  <div className="col-span-1 md:col-span-2 space-y-1.5">
                    <ShLabel>Expressão Cron</ShLabel>
                    <ShInput
                      placeholder="0 0 * * * ?"
                      value={config.cronExpression}
                      onChange={(event) =>
                        setConfig((previous) => ({
                          ...previous,
                          cronExpression: event.target.value,
                        }))
                      }
                    />
                  </div>
                )}
              </div>
            </section>

            <ShSeparator />

            {/* ── Árvore de Migração ── */}
            <section className="space-y-4 pb-4">
              <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Estrutura de Migração (Tree)
              </h3>

              <MigrationNode
                node={migration}
                targetDb={config.targetDb}
                onChange={setMigration}
                isRoot
              />
            </section>
          </ShSheetBody>

          <div className="px-6 py-4 border-t bg-muted/30">
            <ShSheetFooter>
              <ShButton variant="outline" onClick={handleClose}>
                Cancelar
              </ShButton>
              <ShButton
                onClick={() => setShowConfirm(true)}
                disabled={!canSubmit || createJob.isPending}
              >
                {createJob.isPending ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : (
                  <Save className="w-4 h-4 mr-2" />
                )}
                Criar Migration
              </ShButton>
            </ShSheetFooter>
          </div>
        </ShSheetContent>
      </ShSheet>

      <ShAlertDialog open={showConfirm} onOpenChange={setShowConfirm}>
        <ShAlertDialogContent>
          <ShAlertDialogHeader>
            <ShAlertDialogTitle className="flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-destructive" />
              Confirmar Criação e Schema
            </ShAlertDialogTitle>
            <ShAlertDialogDescription>
              A criação deste job irá gerar novas tabelas no banco de destino. Se as tabelas já
              existirem, elas podem ser alteradas para incluir novas colunas ou chaves estrangeiras.
              <br />
              <br />
              <strong>QUER REALMENTE PROSSEGUIR?</strong>
            </ShAlertDialogDescription>
          </ShAlertDialogHeader>
          <ShAlertDialogFooter>
            <ShAlertDialogCancel>Cancelar</ShAlertDialogCancel>
            <ShAlertDialogAction
              onClick={handleSubmit}
              className="bg-destructive hover:bg-destructive/90 text-destructive-foreground"
            >
              Sim, Criar Job
            </ShAlertDialogAction>
          </ShAlertDialogFooter>
        </ShAlertDialogContent>
      </ShAlertDialog>
    </>
  );
};
