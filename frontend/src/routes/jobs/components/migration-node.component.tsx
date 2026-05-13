import { useState, useEffect } from 'react';
import { useResolveSharePoint } from '@lib/hooks/sharepoint.hook';
import { useAdapterTypes } from '@lib/hooks/adapters.hook';
import type {
  JobNode,
  FieldMapping,
  CanonicalType,
  TargetDb,
  CustomFieldDefinition,
  CustomFunction,
} from '../jobs.type';
import {
  ShCard,
  ShCardContent,
  ShCardHeader,
  ShCardTitle,
} from '@lib/components/sh-card/card.component';
import { ShInput } from '@lib/components/sh-input/input.component';
import { ShLabel } from '@lib/components/sh-label/label.component';
import { ShButton } from '@lib/components/sh-button/button.component';
import { ShSelect, ShSelectItem } from '@lib/components/sh-select/select.component';
import {
  ShPopover,
  ShPopoverTrigger,
  ShPopoverContent,
} from '@lib/components/sh-popover/popover.component';
import {
  ShTable,
  ShTableHeader,
  ShTableRow,
  ShTableHead,
  ShTableBody,
  ShTableCell,
} from '@lib/components/sh-table/table.component';
import {
  ShDialog,
  ShDialogContent,
  ShDialogHeader,
  ShDialogTitle,
  ShDialogFooter,
  ShDialogTrigger,
} from '@lib/components/sh-dialog/dialog.component';
import {
  Loader2,
  Plus,
  Trash2,
  ChevronRight,
  ChevronDown,
  Settings2,
  CheckCircle2,
  Cpu,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@lib/utils/cn.util';

const NONE_NATIVE = '__none__';
const CUSTOM_FUNCTIONS: { label: string; value: CustomFunction }[] = [
  { label: 'Data/Hora Atual (UTC-3)', value: 'CURRENT_TIMESTAMP_UTC_3' },
  { label: 'Data Atual (BR)', value: 'CURRENT_DATE_BR' },
  { label: 'Gerar UUID', value: 'UUID_GEN' },
  { label: 'Valor Estático (Texto)', value: 'STATIC_VALUE' },
];

const parseNativeType = (fullType: string) => {
  const match = fullType.match(/^([^(]+)(?:\((.*)\))?$/);
  if (!match) return { base: fullType, params: [] as string[] };
  const base = match[1].trim();
  const params = match[2] ? match[2].split(',').map((param) => param.trim()) : [];
  return { base, params };
};

const formatNativeType = (base: string, params: string[]) => {
  const filteredParams = params.map((param) => param.trim());
  if (filteredParams.length === 0 || filteredParams.every((param) => param === '')) return base;
  return `${base}(${filteredParams.join(',')})`;
};

interface MigrationNodeProps {
  node: JobNode;
  onChange: (newNode: JobNode) => void;
  onRemove?: () => void;
  targetDb: TargetDb;
  isRoot?: boolean;
}

export const MigrationNode = ({
  node,
  onChange,
  onRemove,
  targetDb,
  isRoot = false,
}: MigrationNodeProps) => {
  const [sharepointUrl, setSharepointUrl] = useState(node.sharepointUrl || '');
  const [isExpanded, setIsExpanded] = useState(true);
  const [availableColumns, setAvailableColumns] = useState<string[]>([]);
  const [customFieldOpen, setCustomFieldOpen] = useState(false);

  const resolveMutation = useResolveSharePoint();
  const { data: adapterTypes } = useAdapterTypes(targetDb);

  // Sync internal state when node.sharepointUrl changes from outside (e.g. during initial edit load)
  useEffect(() => {
    if (node.sharepointUrl && sharepointUrl === '') {
      setSharepointUrl(node.sharepointUrl);
    }
  }, [node.sharepointUrl]);

  // Initialize available columns from existing mappings if any
  useEffect(() => {
    const keys = Object.keys(node.fieldMappings);
    if (keys.length > 0 && availableColumns.length === 0) {
      setTimeout(() => setAvailableColumns(keys), 0);
    }
  }, [node.fieldMappings, availableColumns.length]);

  const handleResolve = async () => {
    if (!sharepointUrl.trim()) return;
    try {
      const data = await resolveMutation.mutateAsync({ url: sharepointUrl });
      setAvailableColumns(data.columns);

      const newMappings: Record<string, FieldMapping> = {};
      data.columns.forEach((columnName) => {
        newMappings[columnName] = {
          column: columnName.toLowerCase().replace(/[\s-]+/g, '_'),
          type: 'TEXT',
          nativeType: '',
          primaryKey: false,
          uniqueKey: false,
        };
      });

      onChange({
        ...node,
        sharepointUrl,
        siteId: data.siteId,
        listId: data.listId,
        fieldMappings: newMappings,
      });
      toast.success('Lista resolvida com sucesso');
    } catch {
      toast.error('Erro ao resolver URL');
    }
  };

  const handleUpdateMapping = (spColumn: string, updates: Partial<FieldMapping>) => {
    const newMappings = { ...node.fieldMappings };

    let finalUpdates = { ...updates };

    if (updates.primaryKey) {
      Object.keys(newMappings).forEach((key) => {
        newMappings[key] = { ...newMappings[key], primaryKey: false };
      });
      finalUpdates.uniqueKey = true;
    }

    if (updates.uniqueKey === false) {
      finalUpdates.primaryKey = false;
    }

    newMappings[spColumn] = { ...newMappings[spColumn], ...finalUpdates };
    onChange({ ...node, fieldMappings: newMappings });
  };

  const handleToggleMapping = (spColumn: string, included: boolean) => {
    const newMappings = { ...node.fieldMappings };
    if (included) {
      newMappings[spColumn] = {
        column: spColumn.toLowerCase().replace(/[\s-]+/g, '_'),
        type: 'TEXT',
        nativeType: '',
        primaryKey: false,
        uniqueKey: false,
      };
    } else {
      delete newMappings[spColumn];
    }
    onChange({ ...node, fieldMappings: newMappings });
  };

  const handleAddCustomField = (def: CustomFieldDefinition) => {
    const newCustomFields = { ...node.customFields, [def.column]: def };
    onChange({ ...node, customFields: newCustomFields });
    setCustomFieldOpen(false);
  };

  const handleRemoveCustomField = (columnName: string) => {
    const newCustomFields = { ...node.customFields };
    delete newCustomFields[columnName];
    onChange({ ...node, customFields: newCustomFields });
  };

  const handleAddChild = () => {
    const newNode: JobNode = {
      siteId: '',
      listId: '',
      tableName: '',
      fieldMappings: {},
      customFields: {},
      foreignKeys: [],
      children: [],
    };
    onChange({
      ...node,
      children: [...node.children, newNode],
    });
  };

  const handleUpdateChild = (index: number, updatedChild: JobNode) => {
    const newChildren = [...node.children];
    newChildren[index] = updatedChild;
    onChange({ ...node, children: newChildren });
  };

  const handleRemoveChild = (index: number) => {
    const newChildren = node.children.filter((_, i) => i !== index);
    onChange({ ...node, children: newChildren });
  };

  return (
    <div className={cn('space-y-4', !isRoot && 'pl-6 border-l-2 border-muted ml-2 mt-4')}>
      <ShCard className={cn(!isRoot && 'bg-muted/10')}>
        <ShCardHeader className="py-3 px-4 flex flex-row items-center justify-between space-y-0">
          <div
            className="flex items-center gap-2 cursor-pointer"
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? (
              <ChevronDown className="w-4 h-4" />
            ) : (
              <ChevronRight className="w-4 h-4" />
            )}
            <ShCardTitle className="text-sm font-medium">
              {isRoot ? 'Raiz da Migração' : `Nodo: ${node.tableName || 'Novo'}`}
              {node.siteId && (
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  (ID: {node.listId.slice(0, 8)}...)
                </span>
              )}
            </ShCardTitle>
          </div>
          <div className="flex items-center gap-2">
            {!isRoot && onRemove && (
              <ShButton
                variant="ghost"
                size="icon-sm"
                onClick={onRemove}
                className="text-destructive"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </ShButton>
            )}

            <ShDialog open={customFieldOpen} onOpenChange={setCustomFieldOpen}>
              <ShDialogTrigger asChild>
                <ShButton
                  variant="outline"
                  size="sm"
                  className="h-8 border-dashed border-primary/50 text-primary"
                >
                  <Cpu className="w-3.5 h-3.5 mr-1" />
                  Campo Virtual
                </ShButton>
              </ShDialogTrigger>
              <CustomFieldForm onAdd={handleAddCustomField} adapterTypes={adapterTypes} />
            </ShDialog>

            <ShButton variant="outline" size="sm" onClick={handleAddChild} className="h-8">
              <Plus className="w-3.5 h-3.5 mr-1" />
              Filho
            </ShButton>
          </div>
        </ShCardHeader>

        {isExpanded && (
          <ShCardContent className="p-4 pt-0 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <ShLabel>URL SharePoint</ShLabel>
                <div className="flex gap-2">
                  <ShInput
                    placeholder="https://..."
                    value={sharepointUrl}
                    onChange={(event) => {
                      const newUrl = event.target.value;
                      setSharepointUrl(newUrl);
                      onChange({ ...node, sharepointUrl: newUrl });
                    }}
                    className="h-8 text-xs"
                  />
                  <ShButton
                    size="sm"
                    onClick={handleResolve}
                    disabled={resolveMutation.isPending || !sharepointUrl.trim()}
                    className="h-8"
                  >
                    {resolveMutation.isPending ? (
                      <Loader2 className="w-3 h-3 animate-spin" />
                    ) : (
                      'Resolver'
                    )}
                  </ShButton>
                </div>
              </div>
              <div className="space-y-1.5">
                <ShLabel>Tabela Destino</ShLabel>
                <ShInput
                  placeholder="Ex: tb_clientes"
                  value={node.tableName}
                  onChange={(event) => onChange({ ...node, tableName: event.target.value })}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            {node.siteId && (
              <div className="rounded-md bg-muted/30 border p-3 space-y-2">
                <p className="text-[10px] font-bold uppercase text-muted-foreground flex items-center gap-1.5">
                  <CheckCircle2 className="w-3 h-3 text-green-500" />
                  Metadados SharePoint Resolvidos
                </p>
                <div className="grid grid-cols-[80px_1fr] gap-x-2 gap-y-1 text-[10px]">
                  <span className="text-muted-foreground">Site ID:</span>
                  <span className="font-mono break-all bg-background px-1.5 py-0.5 rounded border">
                    {node.siteId}
                  </span>
                  <span className="text-muted-foreground">List ID:</span>
                  <span className="font-mono break-all bg-background px-1.5 py-0.5 rounded border">
                    {node.listId}
                  </span>
                </div>
              </div>
            )}

            {(availableColumns.length > 0 || Object.keys(node.customFields || {}).length > 0) && (
              <div className="rounded-md border overflow-hidden">
                <ShTable>
                  <ShTableHeader className="bg-muted/50">
                    <ShTableRow>
                      <ShTableHead className="w-10 h-8 px-3">
                        <input
                          type="checkbox"
                          checked={
                            availableColumns.length > 0 &&
                            availableColumns.every((columnName) => !!node.fieldMappings[columnName])
                          }
                          onChange={(event) => {
                            const allMappings = availableColumns.reduce(
                              (accumulator, columnName) => {
                                if (event.target.checked) {
                                  accumulator[columnName] = node.fieldMappings[columnName] || {
                                    column: columnName.toLowerCase().replace(/[\s-]+/g, '_'),
                                    type: 'TEXT',
                                    nativeType: '',
                                    primaryKey: false,
                                  };
                                }
                                return accumulator;
                              },
                              {} as Record<string, FieldMapping>,
                            );
                            onChange({ ...node, fieldMappings: allMappings });
                          }}
                        />
                      </ShTableHead>
                      <ShTableHead className="w-10 h-8 px-3 text-center">PK</ShTableHead>
                      <ShTableHead className="w-10 h-8 px-3 text-center">UQ</ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Campo Fonte
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Coluna Destino
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Tipo Canônico
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Tipo Nativo
                      </ShTableHead>
                      <ShTableHead className="w-10 h-8 px-3"></ShTableHead>
                    </ShTableRow>
                  </ShTableHeader>
                  <ShTableBody>
                    {/* Campos SharePoint */}
                    {availableColumns.map((spColumn) => {
                      const mapping = node.fieldMappings[spColumn];
                      const included = !!mapping;
                      const { base: nativeBase, params: nativeParams } = parseNativeType(
                        mapping?.nativeType || '',
                      );
                      const nativeDefinition = adapterTypes?.nativeTypes.find(
                        (typeDef) => typeDef.name === nativeBase,
                      );

                      return (
                        <ShTableRow key={spColumn} className={cn(!included && 'opacity-40')}>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            <input
                              type="checkbox"
                              checked={included}
                              onChange={(event) =>
                                handleToggleMapping(spColumn, event.target.checked)
                              }
                            />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            <input
                              type="checkbox"
                              checked={mapping?.primaryKey || false}
                              disabled={!included}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, { primaryKey: event.target.checked })
                              }
                              className="accent-primary"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            <input
                              type="checkbox"
                              checked={mapping?.uniqueKey || false}
                              disabled={!included}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, { uniqueKey: event.target.checked })
                              }
                              className="accent-primary"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <div className="font-mono text-[12px] leading-none flex items-center gap-1.5">
                              {spColumn}
                            </div>

                            {included && (
                              <div className="text-[9px] text-muted-foreground mt-1 flex items-center gap-1">
                                <span>→</span>
                                <span className="font-bold text-primary">
                                  {mapping.nativeType ||
                                    adapterTypes?.canonical[mapping.type] ||
                                    '...'}
                                </span>
                              </div>
                            )}
                          </ShTableCell>

                          <ShTableCell className="py-1.5 px-3">
                            <ShInput
                              value={mapping?.column || ''}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, {
                                  column: event.target.value,
                                })
                              }
                              disabled={!included}
                              className="h-9 py-0 px-2"
                            />
                          </ShTableCell>

                          <ShTableCell className="py-1.5 px-3">
                            <ShSelect
                              value={mapping?.type || 'TEXT'}
                              onValueChange={(value) =>
                                handleUpdateMapping(spColumn, {
                                  type: value as CanonicalType,
                                })
                              }
                              disabled={!included}
                              size="sm"
                            >
                              {Object.keys(adapterTypes?.canonical || {}).map((typeName) => (
                                <ShSelectItem
                                  key={typeName}
                                  value={typeName}
                                  className="text-[10px]"
                                >
                                  {typeName}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <ShPopover>
                              <ShPopoverTrigger asChild>
                                <ShButton
                                  variant="outline"
                                  size="sm"
                                  disabled={!included}
                                  className={cn(
                                    'h-7 w-full justify-start font-mono text-[10px] px-2',
                                    !mapping?.nativeType && 'text-muted-foreground italic',
                                  )}
                                >
                                  <Settings2 className="w-3 h-3 mr-1.5 opacity-50" />
                                  {mapping?.nativeType || '(canônico)'}
                                </ShButton>
                              </ShPopoverTrigger>
                              <ShPopoverContent className="w-80 p-4 space-y-4" side="left">
                                <div className="space-y-1.5">
                                  <ShLabel className="text-[11px] font-bold">Tipo Base</ShLabel>
                                  <ShSelect
                                    value={nativeBase || NONE_NATIVE}
                                    onValueChange={(value) =>
                                      handleUpdateMapping(spColumn, {
                                        nativeType: value === NONE_NATIVE ? '' : value,
                                      })
                                    }
                                  >
                                    <ShSelectItem
                                      value={NONE_NATIVE}
                                      className="text-xs text-muted-foreground"
                                    >
                                      (Usar Tipo Canônico)
                                    </ShSelectItem>
                                    {adapterTypes?.nativeTypes.map((typeDef) => (
                                      <ShSelectItem
                                        key={typeDef.name}
                                        value={typeDef.name}
                                        className="text-xs"
                                      >
                                        {typeDef.name}
                                      </ShSelectItem>
                                    ))}
                                  </ShSelect>
                                </div>

                                {nativeDefinition && nativeDefinition.params.length > 0 && (
                                  <div className="space-y-3 pt-2 border-t">
                                    <ShLabel className="text-[11px] font-bold">
                                      Parâmetros do Tipo
                                    </ShLabel>
                                    <div className="grid grid-cols-2 gap-3">
                                      {nativeDefinition.params.map((paramSpec, paramIndex) => {
                                        const effectiveMax = paramSpec.max;

                                        return (
                                          <div key={paramIndex} className="space-y-1">
                                            <ShLabel className="text-[10px] text-muted-foreground uppercase">
                                              {paramSpec.label} (min: {paramSpec.min}, max:{' '}
                                              {effectiveMax})
                                            </ShLabel>
                                            <ShInput
                                              type="number"
                                              value={nativeParams[paramIndex] || ''}
                                              onChange={(event) => {
                                                const numericValue = parseInt(event.target.value);
                                                if (isNaN(numericValue)) return;

                                                // Enforce range
                                                const clampedValue = Math.max(
                                                  paramSpec.min,
                                                  Math.min(effectiveMax, numericValue),
                                                );

                                                const updatedParams = [...nativeParams];
                                                while (
                                                  updatedParams.length <
                                                  nativeDefinition.params.length
                                                )
                                                  updatedParams.push('');
                                                updatedParams[paramIndex] = String(clampedValue);
                                                handleUpdateMapping(spColumn, {
                                                  nativeType: formatNativeType(
                                                    nativeBase,
                                                    updatedParams,
                                                  ),
                                                });
                                              }}
                                              className="h-8 text-xs"
                                              placeholder={`${paramSpec.min}-${effectiveMax}`}
                                            />
                                          </div>
                                        );
                                      })}
                                    </div>
                                  </div>
                                )}
                              </ShPopoverContent>
                            </ShPopover>
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3"></ShTableCell>
                        </ShTableRow>
                      );
                    })}

                    {/* Campos Virtuais (Custom Fields) */}
                    {Object.values(node.customFields || {}).map((customDef) => {
                      const { base: nativeBase, params: nativeParams } = parseNativeType(
                        customDef.nativeType || '',
                      );
                      const nativeDefinition = adapterTypes?.nativeTypes.find(
                        (typeDefinition) => typeDefinition.name === nativeBase,
                      );
                      const customFunction = CUSTOM_FUNCTIONS.find(
                        (cf) => cf.value === customDef.function,
                      );

                      return (
                        <ShTableRow
                          key={customDef.column}
                          className="bg-primary/[0.03] border-l-2 border-l-primary"
                        >
                          <ShTableCell className="py-1.5 px-3 text-center">
                            <Cpu className="w-3.5 h-3.5 text-primary opacity-50 mx-auto" />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            {/* Virtual fields are usually not PKs, but we could add if needed */}
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            {/* Virtual fields are usually not Unique Keys */}
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <div className="flex flex-col">
                              <div className="text-[11px] font-bold text-primary flex items-center gap-1">
                                {customFunction?.label || customDef.function}
                              </div>
                              <div className="text-[9px] text-muted-foreground flex items-center gap-1">
                                <span>→</span>
                                <span className="font-bold">
                                  {customDef.nativeType ||
                                    adapterTypes?.canonical[customDef.type] ||
                                    '...'}
                                </span>
                              </div>
                            </div>
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <ShInput
                              value={customDef.column}
                              onChange={(event) =>
                                handleAddCustomField({ ...customDef, column: event.target.value })
                              }
                              className="h-9 py-0 px-2 font-bold text-primary"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <ShSelect
                              value={customDef.type}
                              onValueChange={(value) =>
                                handleAddCustomField({ ...customDef, type: value as CanonicalType })
                              }
                              size="sm"
                            >
                              {Object.keys(adapterTypes?.canonical || {}).map((typeName) => (
                                <ShSelectItem
                                  key={typeName}
                                  value={typeName}
                                  className="text-[10px]"
                                >
                                  {typeName}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <ShPopover>
                              <ShPopoverTrigger asChild>
                                <ShButton
                                  variant="outline"
                                  size="sm"
                                  className="h-7 w-full justify-start font-mono text-[10px] px-2 border-primary/30"
                                >
                                  <Settings2 className="w-3 h-3 mr-1.5 text-primary opacity-50" />
                                  {customDef.nativeType || '(canônico)'}
                                </ShButton>
                              </ShPopoverTrigger>
                              <ShPopoverContent className="w-80 p-4 space-y-4" side="left">
                                <div className="space-y-1.5">
                                  <ShLabel className="text-[11px] font-bold">
                                    Tipo Base (Virtual)
                                  </ShLabel>
                                  <ShSelect
                                    value={nativeBase || NONE_NATIVE}
                                    onValueChange={(value) =>
                                      handleAddCustomField({
                                        ...customDef,
                                        nativeType: value === NONE_NATIVE ? '' : value,
                                      })
                                    }
                                  >
                                    <ShSelectItem
                                      value={NONE_NATIVE}
                                      className="text-xs text-muted-foreground"
                                    >
                                      (Canônico)
                                    </ShSelectItem>
                                    {adapterTypes?.nativeTypes.map((nt) => (
                                      <ShSelectItem
                                        key={nt.name}
                                        value={nt.name}
                                        className="text-xs"
                                      >
                                        {nt.name}
                                      </ShSelectItem>
                                    ))}
                                  </ShSelect>
                                </div>
                                {nativeDefinition && nativeDefinition.params.length > 0 && (
                                  <div className="grid grid-cols-2 gap-3 pt-2 border-t">
                                    {nativeDefinition.params.map((paramSpec, paramIndex) => (
                                      <div key={paramIndex} className="space-y-1">
                                        <ShLabel className="text-[10px] uppercase">
                                          {paramSpec.label}
                                        </ShLabel>
                                        <ShInput
                                          type="number"
                                          value={nativeParams[paramIndex] || ''}
                                          onChange={(event) => {
                                            const numericValue = parseInt(event.target.value);
                                            if (isNaN(numericValue)) return;
                                            const clampedValue = Math.max(
                                              paramSpec.min,
                                              Math.min(paramSpec.max, numericValue),
                                            );
                                            const updatedParams = [...nativeParams];
                                            while (
                                              updatedParams.length < nativeDefinition.params.length
                                            )
                                              updatedParams.push('');
                                            updatedParams[paramIndex] = String(clampedValue);
                                            handleAddCustomField({
                                              ...customDef,
                                              nativeType: formatNativeType(
                                                nativeBase,
                                                updatedParams,
                                              ),
                                            });
                                          }}
                                          className="h-8 text-xs"
                                        />
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </ShPopoverContent>
                            </ShPopover>
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3 text-center">
                            <ShButton
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handleRemoveCustomField(customDef.column)}
                              className="text-destructive h-7 w-7"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </ShButton>
                          </ShTableCell>
                        </ShTableRow>
                      );
                    })}
                  </ShTableBody>
                </ShTable>
              </div>
            )}
          </ShCardContent>
        )}
      </ShCard>

      {isExpanded &&
        node.children.map((child, index) => (
          <MigrationNode
            key={index}
            node={child}
            targetDb={targetDb}
            onChange={(updated) => handleUpdateChild(index, updated)}
            onRemove={() => handleRemoveChild(index)}
          />
        ))}
    </div>
  );
};

const FUNCTION_AUTO_TYPE: Record<CustomFunction, CanonicalType> = {
  CURRENT_TIMESTAMP_UTC_3: 'DATETIME',
  CURRENT_DATE_BR: 'DATE',
  UUID_GEN: 'TEXT',
  STATIC_VALUE: 'TEXT',
};

const FUNCTION_ALLOWED_TYPES: Record<CustomFunction, string[]> = {
  CURRENT_TIMESTAMP_UTC_3: ['DATETIME', 'TEXT', 'DATE'],
  CURRENT_DATE_BR: ['DATE', 'TEXT'],
  UUID_GEN: ['TEXT'],
  STATIC_VALUE: ['TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'],
};

const CustomFieldForm = ({
  onAdd,
  adapterTypes,
}: {
  onAdd: (definition: CustomFieldDefinition) => void;
  adapterTypes?: any;
}) => {
  const [columnName, setColumnName] = useState('');
  const [staticValue, setStaticValue] = useState('');
  const [selectedFunction, setSelectedFunction] =
    useState<CustomFunction>('CURRENT_TIMESTAMP_UTC_3');
  const [canonicalType, setCanonicalType] = useState<CanonicalType>('DATETIME');

  return (
    <ShDialogContent size="sm">
      <ShDialogHeader>
        <ShDialogTitle>Novo Campo Virtual</ShDialogTitle>
      </ShDialogHeader>
      <div className="space-y-4 py-4">
        <div className="space-y-1.5">
          <ShLabel>Função Geradora</ShLabel>
          <ShSelect
            value={selectedFunction}
            onValueChange={(value) => {
              const func = value as CustomFunction;
              setSelectedFunction(func);
              setCanonicalType(FUNCTION_AUTO_TYPE[func]);
            }}
          >
            {CUSTOM_FUNCTIONS.map((customFunc) => (
              <ShSelectItem key={customFunc.value} value={customFunc.value}>
                {customFunc.label}
              </ShSelectItem>
            ))}
          </ShSelect>
        </div>

        {selectedFunction === 'STATIC_VALUE' && (
          <div className="space-y-1.5">
            <ShLabel>Valor Estático</ShLabel>
            <ShInput
              placeholder="Digite o valor..."
              value={staticValue}
              onChange={(event) => setStaticValue(event.target.value)}
            />
          </div>
        )}

        <div className="space-y-1.5">
          <ShLabel>Nome da Coluna no Banco</ShLabel>
          <ShInput
            placeholder="ex: data_sincronizacao"
            value={columnName}
            onChange={(event) => setColumnName(event.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <ShLabel>Tipo Canônico</ShLabel>
          <ShSelect
            value={canonicalType}
            onValueChange={(value) => setCanonicalType(value as CanonicalType)}
          >
            {Object.keys(adapterTypes?.canonical || {})
              .filter((typeName) => FUNCTION_ALLOWED_TYPES[selectedFunction].includes(typeName))
              .map((typeName) => (
                <ShSelectItem key={typeName} value={typeName}>
                  {typeName}
                </ShSelectItem>
              ))}
          </ShSelect>
        </div>
      </div>
      <ShDialogFooter>
        <ShButton
          onClick={() =>
            onAdd({
              column: columnName || 'nova_coluna',
              type: canonicalType,
              nativeType: '',
              function: selectedFunction,
              staticValue: selectedFunction === 'STATIC_VALUE' ? staticValue : undefined,
            })
          }
        >
          Adicionar Campo
        </ShButton>
      </ShDialogFooter>
    </ShDialogContent>
  );
};
