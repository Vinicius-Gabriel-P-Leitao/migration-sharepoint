import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useUpdateJob } from '@/lib/hooks/jobs.hook';
import { connectionsService } from '@/routes/connections/services/connections.service';
import { adaptersService } from '@/routes/adapters/services/adapters.service';
import { sharepointService } from '@/routes/sharepoint/services/sharepoint.service';
import type { CanonicalType, IntervalUnit, JobRequest, JobResponse, ScheduleType, TargetDb } from '@/routes/jobs/jobs.type';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetBody,
  SheetFooter,
  SheetTitle,
  SheetDescription,
} from '@/lib/components/ui/sheet';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/lib/components/ui/tabs';
import { Input } from '@/lib/components/ui/input';
import { Label } from '@/lib/components/ui/label';
import { ShSelect, ShSelectItem, ShSelectSeparator } from '@/lib/components/sh-select/select.component';
import { ShButton } from '@/lib/components/sh-button/button.component';
import { Loader2, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils/cn.util';

const NONE_NATIVE = '__none__';
const TARGET_DBS: TargetDb[] = ['MYSQL', 'POSTGRESQL', 'MONGODB'];
const INTERVAL_UNITS: IntervalUnit[] = ['MINUTES', 'HOURS', 'DAYS'];
const FALLBACK_CANONICAL: CanonicalType[] = ['TEXT', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'];

interface EditMapping {
  spColumn: string;
  dbColumn: string;
  canonicalType: CanonicalType;
  nativeType: string;
  included: boolean;
}

interface EditJobSheetProps {
  job: JobResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const EditJobSheet = ({ job, open, onOpenChange }: EditJobSheetProps) => {
  const [name, setName] = useState('');
  const [targetDb, setTargetDb] = useState<TargetDb>('MYSQL');
  const [connectionKey, setConnectionKey] = useState('');
  const [tableName, setTableName] = useState('');
  const [pageSize, setPageSize] = useState(1000);
  const [scheduleType, setScheduleType] = useState<ScheduleType>('MANUAL');
  const [intervalValue, setIntervalValue] = useState(1);
  const [intervalUnit, setIntervalUnit] = useState<IntervalUnit>('HOURS');
  const [cronExpression, setCronExpression] = useState('');
  const [siteId, setSiteId] = useState('');
  const [listId, setListId] = useState('');
  const [resolveUrl, setResolveUrl] = useState('');
  const [mappings, setMappings] = useState<EditMapping[]>([]);

  const updateJob = useUpdateJob(job?.id ?? 0);

  const { data: connections, isLoading: loadingConnections } = useQuery({
    queryKey: ['connections'],
    queryFn: connectionsService.getAll,
    enabled: open,
  });

  const { data: adapterTypes, isLoading: loadingTypes } = useQuery({
    queryKey: ['adapter-types', targetDb],
    queryFn: () => adaptersService.getTypes(targetDb),
    enabled: open,
  });

  const canonicalTypes: CanonicalType[] = adapterTypes
    ? (Object.keys(adapterTypes.canonical) as CanonicalType[])
    : FALLBACK_CANONICAL;

  // Initialize form when job changes
  useEffect(() => {
    if (!job) return;
    setName(job.name);
    setTargetDb(job.targetDb);
    setConnectionKey(job.connectionKey);
    setTableName(job.tableName);
    setPageSize(job.pageSize);
    setScheduleType(job.scheduleType);
    setIntervalValue(job.intervalValue ?? 1);
    setIntervalUnit(job.intervalUnit ?? 'HOURS');
    setCronExpression(job.cronExpression ?? '');
    setSiteId(job.siteId);
    setListId(job.listId);
    setResolveUrl('');
    setMappings(
      Object.entries(job.fieldMappings).map(([spCol, fm]) => ({
        spColumn: spCol,
        dbColumn: fm.column,
        canonicalType: fm.type as CanonicalType,
        nativeType: fm.nativeType || '',
        included: true,
      }))
    );
  }, [job]);

  const [resolving, setResolving] = useState(false);
  const handleResolve = async () => {
    if (!resolveUrl.trim()) return;
    setResolving(true);
    try {
      const result = await sharepointService.resolve({ url: resolveUrl });
      setSiteId(result.siteId);
      setListId(result.listId);
      // Merge new columns, preserving existing mappings
      const existingKeys = new Set(mappings.map((m) => m.spColumn));
      const newMappings: EditMapping[] = result.columns
        .filter((col) => !existingKeys.has(col))
        .map((col) => ({
          spColumn: col,
          dbColumn: col.toLowerCase().replace(/[\s-]+/g, '_'),
          canonicalType: 'TEXT',
          nativeType: '',
          included: false,
        }));
      setMappings((prev) => [...prev, ...newMappings]);
      toast.success(`Lista re-resolvida: ${result.columns.length} colunas`);
    } catch {
      toast.error('Erro ao resolver URL');
    } finally {
      setResolving(false);
    }
  };

  const handleMappingChange = <K extends keyof EditMapping>(
    idx: number,
    field: K,
    value: EditMapping[K]
  ) => {
    setMappings((prev) => prev.map((m, i) => (i === idx ? { ...m, [field]: value } : m)));
  };

  const handleTargetDbChange = (db: TargetDb) => {
    setTargetDb(db);
    setConnectionKey('');
    setMappings((prev) => prev.map((m) => ({ ...m, nativeType: '' })));
  };

  const selectedMappings = mappings.filter((m) => m.included);

  const handleSave = async () => {
    if (!job) return;

    const fieldMappings: JobRequest['fieldMappings'] = {};
    for (const m of selectedMappings) {
      fieldMappings[m.spColumn] = {
        column: m.dbColumn,
        type: m.canonicalType,
        nativeType: m.nativeType,
      };
    }

    const payload: JobRequest = {
      name,
      siteId,
      listId,
      pageSize,
      fieldMappings,
      targetDb,
      connectionKey,
      tableName,
      scheduleType,
      ...(scheduleType === 'INTERVAL' && { intervalValue, intervalUnit }),
      ...(scheduleType === 'CRON' && { cronExpression }),
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
    tableName.trim() !== '' &&
    selectedMappings.length > 0 &&
    (scheduleType !== 'CRON' || cronExpression.trim() !== '');

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-2xl w-full flex flex-col p-0">
        <SheetHeader>
          <SheetTitle>Editar Job</SheetTitle>
          <SheetDescription className="truncate">{job?.name}</SheetDescription>
        </SheetHeader>

        <SheetBody className="p-0 flex-1 overflow-hidden">
          <Tabs defaultValue="geral" className="flex flex-col h-full">
            <div className="px-6 pt-4 border-b">
              <TabsList>
                <TabsTrigger value="geral">Geral</TabsTrigger>
                <TabsTrigger value="campos">
                  Campos
                  <span className="ml-1.5 text-[10px] bg-muted rounded-full px-1.5 py-0.5">
                    {selectedMappings.length}
                  </span>
                </TabsTrigger>
                <TabsTrigger value="sharepoint">SharePoint</TabsTrigger>
              </TabsList>
            </div>

            {/* ── Geral ── */}
            <TabsContent value="geral" className="flex-1 overflow-y-auto px-6 py-5 space-y-4">
              <div className="space-y-1.5">
                <Label>Nome do Job</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>Banco de Destino</Label>
                  <ShSelect
                    value={targetDb}
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
                  <Label>Tabela Destino</Label>
                  <Input value={tableName} onChange={(e) => setTableName(e.target.value)} />
                </div>

                <div className="space-y-1.5">
                  <Label>Page Size</Label>
                  <Input
                    type="number"
                    min={1}
                    max={5000}
                    value={pageSize}
                    onChange={(e) =>
                      setPageSize(Math.max(1, parseInt(e.target.value) || 1000))
                    }
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label>Agendamento</Label>
                <ShSelect
                  value={scheduleType}
                  onValueChange={(v) => setScheduleType(v as ScheduleType)}
                >
                  <ShSelectItem value="MANUAL">Manual</ShSelectItem>
                  <ShSelectItem value="INTERVAL">Intervalo recorrente</ShSelectItem>
                  <ShSelectItem value="CRON">Expressão Cron</ShSelectItem>
                  <ShSelectItem value="CONTINUOUS">Contínuo</ShSelectItem>
                </ShSelect>
              </div>

              {scheduleType === 'INTERVAL' && (
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label>A cada</Label>
                    <Input
                      type="number"
                      min={1}
                      value={intervalValue}
                      onChange={(e) =>
                        setIntervalValue(Math.max(1, parseInt(e.target.value) || 1))
                      }
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>Unidade</Label>
                    <ShSelect
                      value={intervalUnit}
                      onValueChange={(v) => setIntervalUnit(v as IntervalUnit)}
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
                  <Label>Expressão Cron</Label>
                  <Input
                    placeholder="0 0 * * *"
                    value={cronExpression}
                    onChange={(e) => setCronExpression(e.target.value)}
                  />
                </div>
              )}
            </TabsContent>

            {/* ── Campos ── */}
            <TabsContent value="campos" className="flex-1 overflow-hidden flex flex-col">
              <div className="flex items-center justify-between px-6 py-3 border-b bg-muted/30">
                <span className="text-xs text-muted-foreground">
                  {selectedMappings.length} / {mappings.length} campos selecionados
                </span>
                {loadingTypes && (
                  <span className="text-xs text-muted-foreground flex items-center gap-1">
                    <Loader2 className="w-3 h-3 animate-spin" />
                    Carregando tipos...
                  </span>
                )}
              </div>
              <div className="flex-1 overflow-y-auto">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-muted/95 backdrop-blur-sm border-b z-10">
                    <tr>
                      <th className="px-4 py-2.5 text-left w-10">
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
                      <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide">Campo SP</th>
                      <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide">Coluna</th>
                      <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide w-[140px]">Canônico</th>
                      <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide w-[140px]">Nativo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {mappings.map((m, idx) => (
                      <tr
                        key={m.spColumn}
                        className={cn(
                          'hover:bg-muted/20 transition-colors',
                          !m.included && 'opacity-40'
                        )}
                      >
                        <td className="px-4 py-2">
                          <input
                            type="checkbox"
                            checked={m.included}
                            onChange={(e) =>
                              handleMappingChange(idx, 'included', e.target.checked)
                            }
                            className="rounded"
                          />
                        </td>
                        <td className="px-4 py-2 font-mono text-xs text-muted-foreground">
                          {m.spColumn}
                        </td>
                        <td className="px-4 py-2">
                          <Input
                            value={m.dbColumn}
                            onChange={(e) => handleMappingChange(idx, 'dbColumn', e.target.value)}
                            disabled={!m.included}
                            className="h-7 text-xs font-mono"
                          />
                        </td>
                        <td className="px-4 py-2">
                          <ShSelect
                            value={m.canonicalType}
                            onValueChange={(v) =>
                              handleMappingChange(idx, 'canonicalType', v as CanonicalType)
                            }
                            disabled={!m.included}
                            size="sm"
                          >
                            {canonicalTypes.map((t) => (
                              <ShSelectItem key={t} value={t}>{t}</ShSelectItem>
                            ))}
                          </ShSelect>
                        </td>
                        <td className="px-4 py-2">
                          <ShSelect
                            value={m.nativeType || NONE_NATIVE}
                            onValueChange={(nativeValue) =>
                              handleMappingChange(idx, 'nativeType', nativeValue === NONE_NATIVE ? '' : nativeValue)
                            }
                            disabled={!m.included || loadingTypes || !adapterTypes}
                            size="sm"
                            placeholder="(canônico)"
                            position="popper"
                          >
                            <ShSelectItem value={NONE_NATIVE}>
                              <span className="text-muted-foreground">(canônico)</span>
                            </ShSelectItem>
                            <ShSelectSeparator />
                            {adapterTypes?.nativeTypes.map((nativeType) => (
                              <ShSelectItem key={nativeType} value={nativeType}>{nativeType}</ShSelectItem>
                            ))}
                          </ShSelect>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </TabsContent>

            {/* ── SharePoint ── */}
            <TabsContent value="sharepoint" className="flex-1 overflow-y-auto px-6 py-5 space-y-4">
              <div className="rounded-lg border bg-muted/30 p-4 space-y-2 text-sm">
                <div className="grid grid-cols-[80px_1fr] gap-2">
                  <span className="text-muted-foreground text-xs">Site ID</span>
                  <span className="font-mono text-xs break-all">{siteId}</span>
                  <span className="text-muted-foreground text-xs">List ID</span>
                  <span className="font-mono text-xs break-all">{listId}</span>
                </div>
              </div>

              <div className="space-y-2">
                <Label>Re-resolver via URL</Label>
                <p className="text-xs text-muted-foreground">
                  Informe uma nova URL de lista SharePoint para atualizar o siteId e listId. Colunas novas serão adicionadas ao mapeamento (desabilitadas por padrão).
                </p>
                <div className="flex gap-2">
                  <Input
                    placeholder="https://tenant.sharepoint.com/sites/..."
                    value={resolveUrl}
                    onChange={(e) => setResolveUrl(e.target.value)}
                  />
                  <ShButton
                    variant="outline"
                    onClick={handleResolve}
                    disabled={!resolveUrl.trim() || resolving}
                  >
                    {resolving ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <RefreshCw className="w-4 h-4" />
                    )}
                  </ShButton>
                </div>
              </div>
            </TabsContent>
          </Tabs>
        </SheetBody>

        <SheetFooter>
          <ShButton variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </ShButton>
          <ShButton onClick={handleSave} disabled={!canSave || updateJob.isPending}>
            {updateJob.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            Salvar Alterações
          </ShButton>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
};
