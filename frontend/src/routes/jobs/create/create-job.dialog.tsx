import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useCreateJob } from '@/lib/hooks/jobs.hook';
import { sharepointService, type SharePointResolveResponse } from '@/routes/sharepoint/services/sharepoint.service';
import { connectionsService } from '@/routes/connections/services/connections.service';
import { adaptersService } from '@/routes/adapters/services/adapters.service';
import type { CanonicalType, IntervalUnit, JobRequest, ScheduleType, TargetDb } from '@/routes/jobs/jobs.type';
import {
  Dialog,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/lib/components/ui/dialog';
import { ShDialogContent } from '@/lib/components/sh-dialog/dialog.component';
import { Input } from '@/lib/components/ui/input';
import { Label } from '@/lib/components/ui/label';
import { ShSelect, ShSelectItem, ShSelectSeparator } from '@/lib/components/sh-select/select.component';
import { ShButton } from '@/lib/components/sh-button/button.component';
import { Loader2, ArrowLeft, Check, CheckCircle2, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils/cn.util';

const NONE_NATIVE = '__none__';
const TARGET_DBS: TargetDb[] = ['MYSQL', 'POSTGRESQL', 'MONGODB'];
const INTERVAL_UNITS: IntervalUnit[] = ['MINUTES', 'HOURS', 'DAYS'];
const FALLBACK_CANONICAL: CanonicalType[] = ['TEXT', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'];

type Step = 1 | 2;

interface ColumnMapping {
  spColumn: string;
  dbColumn: string;
  canonicalType: CanonicalType;
  nativeType: string;
  included: boolean;
}

interface JobConfig {
  name: string;
  targetDb: TargetDb;
  connectionKey: string;
  tableName: string;
  pageSize: number;
  scheduleType: ScheduleType;
  intervalValue: number;
  intervalUnit: IntervalUnit;
  cronExpression: string;
}

const defaultConfig: JobConfig = {
  name: '',
  targetDb: 'MYSQL',
  connectionKey: '',
  tableName: '',
  pageSize: 1000,
  scheduleType: 'MANUAL',
  intervalValue: 1,
  intervalUnit: 'HOURS',
  cronExpression: '',
};

interface CreateJobDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const CreateJobDialog = ({ open, onOpenChange }: CreateJobDialogProps) => {
  const [step, setStep] = useState<Step>(1);
  const [sharepointUrl, setSharepointUrl] = useState('');
  const [resolved, setResolved] = useState<SharePointResolveResponse | null>(null);
  const [mappings, setMappings] = useState<ColumnMapping[]>([]);
  const [config, setConfig] = useState<JobConfig>(defaultConfig);

  const createJob = useCreateJob();

  const { data: connections, isLoading: loadingConnections } = useQuery({
    queryKey: ['connections'],
    queryFn: connectionsService.getAll,
    enabled: step === 2,
  });

  const { data: adapterTypes, isLoading: loadingTypes } = useQuery({
    queryKey: ['adapter-types', config.targetDb],
    queryFn: () => adaptersService.getTypes(config.targetDb),
    enabled: step === 2,
  });

  const canonicalTypes: CanonicalType[] = adapterTypes
    ? (Object.keys(adapterTypes.canonical) as CanonicalType[])
    : FALLBACK_CANONICAL;

  const resolveMutation = useMutation({
    mutationFn: sharepointService.resolve,
    onSuccess: (data) => {
      setResolved(data);
      setMappings(
        data.columns.map((col) => ({
          spColumn: col,
          dbColumn: col.toLowerCase().replace(/[\s-]+/g, '_'),
          canonicalType: 'TEXT',
          nativeType: '',
          included: true,
        }))
      );
    },
    onError: () => toast.error('Erro ao resolver URL do SharePoint'),
  });

  const handleTargetDbChange = (db: TargetDb) => {
    setConfig((p) => ({ ...p, targetDb: db, connectionKey: '' }));
    setMappings((prev) => prev.map((m) => ({ ...m, nativeType: '' })));
  };

  const handleMappingChange = <K extends keyof ColumnMapping>(
    idx: number,
    field: K,
    value: ColumnMapping[K]
  ) => {
    setMappings((prev) => prev.map((m, i) => (i === idx ? { ...m, [field]: value } : m)));
  };

  const selectedMappings = mappings.filter((m) => m.included);

  const handleSubmit = async () => {
    if (!resolved) return;

    const fieldMappings: JobRequest['fieldMappings'] = {};
    for (const m of selectedMappings) {
      fieldMappings[m.spColumn] = {
        column: m.dbColumn,
        type: m.canonicalType,
        nativeType: m.nativeType,
      };
    }

    const payload: JobRequest = {
      name: config.name,
      siteId: resolved.siteId,
      listId: resolved.listId,
      pageSize: config.pageSize,
      fieldMappings,
      targetDb: config.targetDb,
      connectionKey: config.connectionKey,
      tableName: config.tableName,
      scheduleType: config.scheduleType,
      ...(config.scheduleType === 'INTERVAL' && {
        intervalValue: config.intervalValue,
        intervalUnit: config.intervalUnit,
      }),
      ...(config.scheduleType === 'CRON' && { cronExpression: config.cronExpression }),
    };

    try {
      await createJob.mutateAsync(payload);
      toast.success('Job criado com sucesso!');
      handleClose();
    } catch {
      toast.error('Erro ao criar job');
    }
  };

  const handleClose = () => {
    onOpenChange(false);
    setTimeout(() => {
      setStep(1);
      setSharepointUrl('');
      setResolved(null);
      setMappings([]);
      setConfig(defaultConfig);
    }, 300);
  };

  const canGoToStep2 = resolved !== null;
  const canSubmit =
    config.name.trim() !== '' &&
    config.connectionKey !== '' &&
    config.tableName.trim() !== '' &&
    selectedMappings.length > 0 &&
    selectedMappings.every((m) => m.dbColumn.trim() !== '') &&
    (config.scheduleType !== 'CRON' || config.cronExpression.trim() !== '');

  const stepIndicator = (
    <div className="flex items-center gap-1 mt-3">
      {(['SharePoint', 'Configuração'] as const).map((label, i) => {
        const s = (i + 1) as Step;
        const done = step > s;
        const active = step === s;
        return (
          <div key={s} className="flex items-center gap-1">
            <div
              className={cn(
                'flex items-center justify-center w-6 h-6 rounded-full text-xs font-semibold border-2 transition-colors shrink-0',
                active && 'border-primary bg-primary text-primary-foreground',
                done && 'border-primary bg-primary/15 text-primary',
                !active && !done && 'border-border text-muted-foreground'
              )}
            >
              {done ? <Check className="w-3 h-3" /> : s}
            </div>
            <span className={cn('text-xs', active ? 'text-foreground font-medium' : 'text-muted-foreground')}>
              {label}
            </span>
            {s < 2 && <div className="mx-2 w-8 h-px bg-border shrink-0" />}
          </div>
        );
      })}
    </div>
  );

  /* ─── STEP 1 ─── */
  if (step === 1) {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <ShDialogContent size="3xl">
          <DialogHeader>
            <DialogTitle>Novo Job de Migração</DialogTitle>
            {stepIndicator}
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="sp-url">URL da lista SharePoint</Label>
              <div className="flex gap-2">
                <Input
                  id="sp-url"
                  placeholder="https://tenant.sharepoint.com/sites/MySite/Lists/MyList/AllItems.aspx"
                  value={sharepointUrl}
                  onChange={(e) => {
                    setSharepointUrl(e.target.value);
                    if (resolved) setResolved(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !resolveMutation.isPending && sharepointUrl.trim())
                      resolveMutation.mutate({ url: sharepointUrl });
                  }}
                />
                <ShButton
                  onClick={() => resolveMutation.mutate({ url: sharepointUrl })}
                  disabled={!sharepointUrl.trim() || resolveMutation.isPending}
                >
                  {resolveMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    'Resolver'
                  )}
                </ShButton>
              </div>
            </div>

            {resolved && (
              <div className="rounded-lg border bg-muted/40 p-4 space-y-2">
                <div className="flex items-center gap-2 text-sm font-medium text-green-600 dark:text-green-400">
                  <CheckCircle2 className="w-4 h-4" />
                  Lista resolvida com sucesso
                </div>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
                  <dt className="text-muted-foreground">Site ID</dt>
                  <dd className="font-mono text-xs truncate">{resolved.siteId}</dd>
                  <dt className="text-muted-foreground">List ID</dt>
                  <dd className="font-mono text-xs truncate">{resolved.listId}</dd>
                  <dt className="text-muted-foreground">Colunas</dt>
                  <dd className="font-medium">{resolved.columns.length} disponíveis</dd>
                </dl>
              </div>
            )}
          </div>

          <DialogFooter>
            <ShButton onClick={() => setStep(2)} disabled={!canGoToStep2}>
              Próximo
            </ShButton>
          </DialogFooter>
        </ShDialogContent>
      </Dialog>
    );
  }

  /* ─── STEP 2 ─── */
  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <ShDialogContent
        showCloseButton
        maxWidth="min(95vw, 1400px)"
        maxHeight="92vh"
        className="flex flex-col gap-0 p-0 overflow-hidden"
      >
        {/* Header */}
        <div className="px-8 pt-6 pb-4 border-b shrink-0">
          <DialogHeader>
            <DialogTitle>Novo Job de Migração</DialogTitle>
            {stepIndicator}
          </DialogHeader>
        </div>

        {/* Scrollable body */}
        <div className="flex-1 overflow-y-auto">
          <div className="px-8 py-6 space-y-8">

            {/* ── Configuração ── */}
            <section className="space-y-4">
              <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
                Configuração do Job
              </h3>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="col-span-2 space-y-1.5">
                  <Label>Nome do Job</Label>
                  <Input
                    placeholder="Ex: Migração Clientes Q1"
                    value={config.name}
                    onChange={(e) => setConfig((p) => ({ ...p, name: e.target.value }))}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Banco de Destino</Label>
                  <ShSelect
                    value={config.targetDb}
                    onValueChange={(v) => handleTargetDbChange(v as TargetDb)}
                  >
                    {TARGET_DBS.map((db) => (
                      <ShSelectItem key={db} value={db}>{db}</ShSelectItem>
                    ))}
                  </ShSelect>
                </div>

                <div className="space-y-1.5">
                  <Label>Conexão</Label>
                  <ShSelect
                    value={config.connectionKey}
                    onValueChange={(v) => setConfig((p) => ({ ...p, connectionKey: v }))}
                    placeholder={loadingConnections ? 'Carregando...' : 'Selecione...'}
                    disabled={loadingConnections}
                  >
                    {connections?.map((c) => (
                      <ShSelectItem key={c.key} value={c.key}>
                        {c.name} ({c.key})
                      </ShSelectItem>
                    ))}
                  </ShSelect>
                  {connections?.length === 0 && (
                    <p className="text-xs text-destructive flex items-center gap-1 mt-1">
                      <AlertCircle className="w-3 h-3" />
                      Nenhuma conexão registrada
                    </p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <Label>Tabela Destino</Label>
                  <Input
                    placeholder="Ex: clientes"
                    value={config.tableName}
                    onChange={(e) => setConfig((p) => ({ ...p, tableName: e.target.value }))}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Page Size</Label>
                  <Input
                    type="number"
                    min={1}
                    max={5000}
                    value={config.pageSize}
                    onChange={(e) =>
                      setConfig((p) => ({ ...p, pageSize: Math.max(1, parseInt(e.target.value) || 1000) }))
                    }
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Agendamento</Label>
                  <ShSelect
                    value={config.scheduleType}
                    onValueChange={(v) => setConfig((p) => ({ ...p, scheduleType: v as ScheduleType }))}
                  >
                    <ShSelectItem value="MANUAL">Manual</ShSelectItem>
                    <ShSelectItem value="INTERVAL">Intervalo recorrente</ShSelectItem>
                    <ShSelectItem value="CRON">Expressão Cron</ShSelectItem>
                    <ShSelectItem value="CONTINUOUS">Contínuo</ShSelectItem>
                  </ShSelect>
                </div>

                {config.scheduleType === 'INTERVAL' && (
                  <>
                    <div className="space-y-1.5">
                      <Label>A cada</Label>
                      <Input
                        type="number"
                        min={1}
                        value={config.intervalValue}
                        onChange={(e) =>
                          setConfig((p) => ({
                            ...p,
                            intervalValue: Math.max(1, parseInt(e.target.value) || 1),
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label>Unidade</Label>
                      <ShSelect
                        value={config.intervalUnit}
                        onValueChange={(v) =>
                          setConfig((p) => ({ ...p, intervalUnit: v as IntervalUnit }))
                        }
                      >
                        {INTERVAL_UNITS.map((u) => (
                          <ShSelectItem key={u} value={u}>{u}</ShSelectItem>
                        ))}
                      </ShSelect>
                    </div>
                  </>
                )}

                {config.scheduleType === 'CRON' && (
                  <div className="col-span-2 space-y-1.5">
                    <Label>Expressão Cron</Label>
                    <Input
                      placeholder="0 0 * * * (todo dia à meia-noite)"
                      value={config.cronExpression}
                      onChange={(e) =>
                        setConfig((p) => ({ ...p, cronExpression: e.target.value }))
                      }
                    />
                  </div>
                )}
              </div>
            </section>

            <div className="border-t" />

            {/* ── Mapeamento de Campos ── */}
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
                  Mapeamento de Campos
                </h3>
                <div className="flex items-center gap-3">
                  {loadingTypes && (
                    <span className="text-xs text-muted-foreground flex items-center gap-1.5">
                      <Loader2 className="w-3 h-3 animate-spin" />
                      Carregando tipos de {config.targetDb}...
                    </span>
                  )}
                  <span className="text-xs text-muted-foreground">
                    {selectedMappings.length} / {mappings.length} campos selecionados
                  </span>
                </div>
              </div>

              <p className="text-xs text-muted-foreground">
                O <strong>Tipo Nativo</strong> tem precedência sobre o Canônico quando definido — use para controle
                fino (ex: <code className="font-mono bg-muted px-1 rounded">VARCHAR(255)</code>,{' '}
                <code className="font-mono bg-muted px-1 rounded">DECIMAL(15,2)</code>).
              </p>

              <div className="rounded-lg border overflow-hidden">
                <div className="overflow-y-auto" style={{ maxHeight: '42vh' }}>
                  <table className="w-full text-sm">
                    <thead className="sticky top-0 z-10 bg-muted/95 backdrop-blur-sm border-b">
                      <tr>
                        <th className="px-4 py-3 w-12 text-left">
                          <input
                            type="checkbox"
                            checked={mappings.length > 0 && mappings.every((m) => m.included)}
                            onChange={(e) =>
                              setMappings((prev) =>
                                prev.map((m) => ({ ...m, included: e.target.checked }))
                              )
                            }
                            className="rounded"
                          />
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide min-w-[160px]">
                          Campo SharePoint
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide min-w-[160px]">
                          Coluna Destino
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide w-[220px]">
                          Tipo Canônico
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide w-[220px]">
                          Tipo Nativo{' '}
                          <span className="normal-case font-normal text-muted-foreground tracking-normal">
                            (opcional)
                          </span>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {mappings.map((m, idx) => (
                        <tr
                          key={m.spColumn}
                          className={cn(
                            'hover:bg-muted/30 transition-colors',
                            !m.included && 'opacity-40'
                          )}
                        >
                          <td className="px-4 py-2.5">
                            <input
                              type="checkbox"
                              checked={m.included}
                              onChange={(e) =>
                                handleMappingChange(idx, 'included', e.target.checked)
                              }
                              className="rounded"
                            />
                          </td>
                          <td className="px-4 py-2.5">
                            <span className="font-mono text-xs">{m.spColumn}</span>
                            {adapterTypes?.canonical[m.canonicalType] && !m.nativeType && (
                              <div className="text-[10px] text-muted-foreground mt-0.5">
                                → {adapterTypes.canonical[m.canonicalType]}
                              </div>
                            )}
                            {m.nativeType && (
                              <div className="text-[10px] text-primary mt-0.5">
                                → {m.nativeType}
                              </div>
                            )}
                          </td>
                          <td className="px-4 py-2.5">
                            <Input
                              value={m.dbColumn}
                              onChange={(e) =>
                                handleMappingChange(idx, 'dbColumn', e.target.value)
                              }
                              disabled={!m.included}
                              className="h-8 text-xs font-mono"
                            />
                          </td>
                          <td className="px-4 py-2.5">
                            <ShSelect
                              value={m.canonicalType}
                              onValueChange={(v) =>
                                handleMappingChange(idx, 'canonicalType', v as CanonicalType)
                              }
                              disabled={!m.included}
                              size="sm"
                            >
                              {canonicalTypes.map((t) => (
                                <ShSelectItem key={t} value={t}>
                                  <span>{t}</span>
                                  {adapterTypes?.canonical[t] && (
                                    <span className="ml-2 text-muted-foreground text-[10px]">
                                      → {adapterTypes.canonical[t]}
                                    </span>
                                  )}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </td>
                          <td className="px-4 py-2.5">
                            <ShSelect
                              value={m.nativeType || NONE_NATIVE}
                              onValueChange={(v) =>
                                handleMappingChange(
                                  idx,
                                  'nativeType',
                                  v === NONE_NATIVE ? '' : v
                                )
                              }
                              disabled={!m.included || loadingTypes || !adapterTypes}
                              size="sm"
                              placeholder="(usar canônico)"
                            >
                              <ShSelectItem value={NONE_NATIVE}>
                                <span className="text-muted-foreground">(usar canônico)</span>
                              </ShSelectItem>
                              <ShSelectSeparator />
                              {adapterTypes?.nativeTypes.map((t) => (
                                <ShSelectItem key={t} value={t}>
                                  {t}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </section>
          </div>
        </div>

        {/* Footer */}
        <div className="shrink-0 border-t px-8 py-4">
          <DialogFooter>
            <ShButton variant="outline" onClick={() => setStep(1)}>
              <ArrowLeft className="w-4 h-4 mr-1" />
              Voltar
            </ShButton>
            <ShButton onClick={handleSubmit} disabled={!canSubmit || createJob.isPending}>
              {createJob.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              Criar Job
            </ShButton>
          </DialogFooter>
        </div>
      </ShDialogContent>
    </Dialog>
  );
};
