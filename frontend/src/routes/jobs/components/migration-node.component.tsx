import { useState, useEffect } from 'react';
import { useResolveSharePoint } from '@lib/hooks/sharepoint.hook';
import { useAdapterTypes } from '@lib/hooks/adapters.hook';
import type { AdapterTypesResponse } from '@lib/services/adapters.service';
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
  ShSheet,
  ShSheetContent,
  ShSheetHeader,
  ShSheetTitle,
  ShSheetFooter,
} from '@lib/components/sh-sheet/sheet.component';
import {
  Loader2,
  Plus,
  Trash2,
  ChevronRight,
  ChevronDown,
  Settings2,
  CheckCircle2,
  Cpu,
  Link as LinkIcon,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@lib/utils/cn.util';

const NONE_NATIVE = '__none__';
const CUSTOM_FUNCTIONS: { label: string; value: CustomFunction }[] = [
  { label: 'Data/Hora Atual (UTC-3)', value: 'CURRENT_TIMESTAMP_UTC_3' },
  { label: 'Data Atual (BR)', value: 'CURRENT_DATE_BR' },
  { label: 'Gerar UUID', value: 'UUID_GEN' },
  { label: 'ID Incremental (Auto)', value: 'AUTO_INCREMENT' },
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

interface ParentColumnOption {
  column: string;
  isKey: boolean;
  type: CanonicalType;
  nativeType?: string;
}

interface MigrationNodeProps {
  node: JobNode;
  onChange: (newNode: JobNode) => void;
  onRemove?: () => void;
  targetDb: TargetDb;
  isRoot?: boolean;
  parentColumns?: ParentColumnOption[];
}

export const MigrationNode = ({
  node,
  onChange,
  onRemove,
  targetDb,
  isRoot = false,
  parentColumns = [],
}: MigrationNodeProps) => {
  const [sharepointUrl, setSharepointUrl] = useState(node.sharepointUrl || '');
  const [isExpanded, setIsExpanded] = useState(true);
  const [availableColumns, setAvailableColumns] = useState<string[]>([]);
  const [customFieldOpen, setCustomFieldOpen] = useState(false);

  const resolveMutation = useResolveSharePoint();
  const { data: adapterTypes } = useAdapterTypes(targetDb);

  const currentColumns = [
    ...Object.values(node.fieldMappings).map((m) => m.column),
    ...Object.values(node.customFields).map((f) => f.column),
  ].filter(Boolean);

  const parentKeyColumns = parentColumns.filter((c) => c.isKey).map((c) => c.column);

  // Sync internal state when node.sharepointUrl changes from outside (e.g. during initial edit load)
  useEffect(() => {
    if (node.sharepointUrl && sharepointUrl === '') {
      setTimeout(() => setSharepointUrl(node.sharepointUrl || ''), 0);
    }
  }, [node.sharepointUrl, sharepointUrl]);

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

    const finalUpdates = { ...updates };

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

  const handleAddForeignKey = () => {
    onChange({
      ...node,
      foreignKeys: [...(node.foreignKeys || []), { localColumn: '', parentColumn: '' }],
    });
  };

  const handleUpdateForeignKey = (index: number, updates: Partial<{ localColumn: string; parentColumn: string }>) => {
    const newFks = [...(node.foreignKeys || [])];
    newFks[index] = { ...newFks[index], ...updates };
    onChange({ ...node, foreignKeys: newFks });
  };

  const handleRemoveForeignKey = (index: number) => {
    const newFks = (node.foreignKeys || []).filter((_, i) => i !== index);
    onChange({ ...node, foreignKeys: newFks });
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
    const newChildren = node.children.filter((_, childIndex) => childIndex !== index);
    onChange({ ...node, children: newChildren });
  };

  const handleAutoCreateFKField = (index: number, localColumn: string, parentColumn: string) => {
    if (!localColumn || !parentColumn) {
      toast.error('Selecione a coluna no pai primeiro');
      return;
    }
    const parentCol = parentColumns.find((c) => c.column === parentColumn);
    if (!parentCol) return;

    const exists = currentColumns.includes(localColumn);
    if (exists) return;

    const cleanNativeType = (nativeType: string) => {
      if (!nativeType) return '';
      // Remove AUTO_INCREMENT, PRIMARY KEY, UNIQUE, NOT NULL, etc.
      // Keeps only the base type and optional precision like VARCHAR(255) or DECIMAL(10,2)
      return nativeType.split(/\s+/).filter(word => 
        !['AUTO_INCREMENT', 'PRIMARY', 'KEY', 'UNIQUE', 'NOT', 'NULL'].includes(word.toUpperCase())
      ).join(' ');
    };

    const newField: CustomFieldDefinition = {
      column: localColumn,
      type: parentCol.type,
      nativeType: cleanNativeType(parentCol.nativeType || ''),
      function: 'STATIC_VALUE',
      staticValue: '',
      primaryKey: false,
      uniqueKey: false,
    };

    // 1. Create the virtual field
    const newCustomFields = { ...node.customFields, [newField.column]: newField };
    
    // 2. Update the localColumn in the current foreign key list
    const newFks = [...(node.foreignKeys || [])];
    newFks[index] = { ...newFks[index], localColumn };

    // 3. Batch update the node
    onChange({ 
      ...node, 
      customFields: newCustomFields,
      foreignKeys: newFks
    });
    
    toast.success(`Campo virtual '${localColumn}' criado para a FK`);
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

            <ShButton
              variant="outline"
              size="sm"
              className="h-8 border-dashed border-primary/50 text-primary-foreground"
              onClick={() => setCustomFieldOpen(true)}
            >
              <Cpu className="w-3.5 h-3.5 mr-1" />
              Campo Virtual
            </ShButton>

            <CustomFieldForm
              onAdd={handleAddCustomField}
              adapterTypes={adapterTypes}
              open={customFieldOpen}
              onOpenChange={setCustomFieldOpen}
            />

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
                <ShTable className="table-fixed w-full">
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
                                    uniqueKey: false,
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
                      <ShTableHead className="w-12 h-10 px-3 text-center text-xs">PK</ShTableHead>
                      <ShTableHead className="w-12 h-10 px-3 text-center text-xs">UQ</ShTableHead>
                      <ShTableHead className="h-10 text-[12px] uppercase font-bold px-3 w-[22%]">
                        Campo Fonte
                      </ShTableHead>
                      <ShTableHead className="h-10 text-[12px] uppercase font-bold px-3 w-[22%]">
                        Coluna Destino
                      </ShTableHead>
                      <ShTableHead className="h-10 text-[12px] uppercase font-bold px-3 w-[180px]">
                        Tipo Canônico
                      </ShTableHead>
                      <ShTableHead className="h-10 text-[12px] uppercase font-bold px-3 w-[190px]">
                        Tipo Nativo
                      </ShTableHead>
                      <ShTableHead className="w-12 h-10 px-3"></ShTableHead>
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
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <input
                              type="checkbox"
                              checked={included}
                              onChange={(event) =>
                                handleToggleMapping(spColumn, event.target.checked)
                              }
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <input
                              type="checkbox"
                              checked={mapping?.primaryKey || false}
                              disabled={!included}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, { primaryKey: event.target.checked })
                              }
                              className="accent-primary h-4 w-4"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <input
                              type="checkbox"
                              checked={mapping?.uniqueKey || false}
                              disabled={!included}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, { uniqueKey: event.target.checked })
                              }
                              className="accent-primary h-4 w-4"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 overflow-hidden">
                            <div className="font-mono text-[13px] font-medium leading-none flex items-center gap-1.5 truncate" title={spColumn}>
                              {spColumn}
                            </div>

                            {included && (
                              <div className="text-[11px] text-muted-foreground mt-1.5 flex items-center gap-1 truncate">
                                <span>→</span>
                                <span className="font-bold text-foreground truncate uppercase">
                                  {mapping.nativeType ||
                                    adapterTypes?.canonical[mapping.type] ||
                                    '...'}
                                </span>
                              </div>
                            )}
                          </ShTableCell>

                          <ShTableCell className="py-2.5 px-3 overflow-hidden">
                            <ShInput
                              value={mapping?.column || ''}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, {
                                  column: event.target.value,
                                })
                              }
                              disabled={!included}
                              className="h-9 py-0 px-2 text-[13px]"
                            />
                          </ShTableCell>

                          <ShTableCell className="py-2.5 px-3 overflow-hidden">
                            <ShSelect
                              value={mapping?.type || 'TEXT'}
                              onValueChange={(value) =>
                                handleUpdateMapping(spColumn, {
                                  type: value as CanonicalType,
                                })
                              }
                              disabled={!included}
                              size="default"
                              className="w-full h-9"
                            >
                              {Object.keys(adapterTypes?.canonical || {}).map((typeName) => (
                                <ShSelectItem
                                  key={typeName}
                                  value={typeName}
                                  className="text-[12px]"
                                >
                                  {typeName}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 overflow-hidden">
                            <ShPopover>
                              <ShPopoverTrigger asChild>
                                <ShButton
                                  variant="outline"
                                  size="default"
                                  disabled={!included}
                                  className={cn(
                                    'h-10 w-full justify-start font-mono text-[13px] px-3 overflow-hidden border-2',
                                    !mapping?.nativeType && 'text-muted-foreground italic border-dashed opacity-70',
                                    mapping?.nativeType && 'font-bold text-foreground border-primary/30',
                                  )}
                                >
                                  <Settings2 className="w-5 h-5 mr-2 opacity-70 shrink-0" />
                                  <span className="truncate">
                                    {mapping?.nativeType || '(Canônico)'}
                                  </span>
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
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <Cpu className="w-4 h-4 text-primary opacity-60 mx-auto" />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <input
                              type="checkbox"
                              checked={customDef.primaryKey || false}
                              onChange={(event) =>
                                handleAddCustomField({
                                  ...customDef,
                                  primaryKey: event.target.checked,
                                  uniqueKey: event.target.checked || customDef.uniqueKey,
                                })
                              }
                              className="accent-primary h-4 w-4"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <input
                              type="checkbox"
                              checked={customDef.uniqueKey || false}
                              onChange={(event) =>
                                handleAddCustomField({
                                  ...customDef,
                                  uniqueKey: event.target.checked,
                                })
                              }
                              className="accent-primary h-4 w-4"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3 overflow-hidden">
                            <div className="flex flex-col truncate">
                              <div className="text-[12px] font-bold text-foreground flex items-center gap-1 truncate" title={customFunction?.label}>
                                {customFunction?.label || customDef.function}
                              </div>
                              <div className="text-[11px] text-muted-foreground flex items-center gap-1 truncate mt-1">
                                <span>→</span>
                                <span className="font-bold truncate uppercase text-foreground">
                                  {customDef.nativeType ||
                                    adapterTypes?.canonical[customDef.type] ||
                                    '...'}
                                </span>
                              </div>
                            </div>
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3">
                            <ShInput
                              value={customDef.column}
                              onChange={(event) =>
                                handleAddCustomField({ ...customDef, column: event.target.value })
                              }
                              className="h-9 py-0 px-2 font-bold text-foreground text-[13px]"
                            />
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3">
                            <ShSelect
                              value={customDef.type}
                              onValueChange={(value) =>
                                handleAddCustomField({ ...customDef, type: value as CanonicalType })
                              }
                              size="default"
                              className="w-full h-9"
                            >
                              {Object.keys(adapterTypes?.canonical || {}).map((typeName) => (
                                <ShSelectItem
                                  key={typeName}
                                  value={typeName}
                                  className="text-[12px]"
                                >
                                  {typeName}
                                </ShSelectItem>
                              ))}
                            </ShSelect>
                          </ShTableCell>
                          <ShTableCell className="py-2.5 px-3">
                            <ShPopover>
                              <ShPopoverTrigger asChild>
                                <ShButton
                                  variant="outline"
                                  size="default"
                                  className={cn(
                                    'h-10 w-full justify-start font-mono text-[13px] px-3 overflow-hidden border-2 border-primary/20',
                                    !customDef.nativeType && 'text-muted-foreground italic border-dashed opacity-70',
                                    customDef.nativeType && 'font-bold text-foreground',
                                  )}
                                >
                                  <Settings2 className="w-5 h-5 mr-2 text-primary opacity-50 shrink-0" />
                                  <span className="truncate">
                                    {customDef.nativeType || '(Canônico)'}
                                  </span>
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
                          <ShTableCell className="py-2.5 px-3 text-center overflow-hidden">
                            <ShButton
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handleRemoveCustomField(customDef.column)}
                              className="text-destructive h-8 w-8"
                            >
                              <Trash2 className="w-4 h-4" />
                            </ShButton>
                          </ShTableCell>
                        </ShTableRow>
                      );
                    })}
                  </ShTableBody>
                </ShTable>
              </div>
            )}

            {/* Chaves Estrangeiras */}
            {!isRoot && (
              <div className="rounded-md border p-3 space-y-3 bg-primary/5 border-primary/20">
                <div className="flex items-center justify-between">
                  <ShLabel className="text-[10px] font-bold uppercase text-primary flex items-center gap-1.5">
                    <LinkIcon className="w-3 h-3" />
                    Chaves Estrangeiras (Relação com Pai)
                  </ShLabel>
                  <ShButton
                    variant="outline"
                    size="sm"
                    onClick={handleAddForeignKey}
                    className="h-7 text-[10px] border-primary/30 text-primary hover:bg-primary/10"
                  >
                    <Plus className="w-3 h-3 mr-1" />
                    Adicionar FK
                  </ShButton>
                </div>

                {node.foreignKeys && node.foreignKeys.length > 0 ? (
                  <div className="space-y-2">
                    {node.foreignKeys.map((fk, index) => (
                      <div
                        key={index}
                        className="flex items-center gap-3 bg-background p-2 rounded border border-primary/10"
                      >
                        <div className="flex-1 space-y-1">
                          <ShLabel className="text-[9px] text-muted-foreground uppercase flex items-center justify-between">
                            Coluna Local
                            {!currentColumns.includes(fk.localColumn) && fk.localColumn && (
                              <span className="text-[8px] text-destructive font-bold animate-pulse">
                                MISSING FIELD
                              </span>
                            )}
                          </ShLabel>
                          <div className="flex gap-1">
                            <ShInput
                              value={fk.localColumn}
                              onChange={(e) => handleUpdateForeignKey(index, { localColumn: e.target.value })}
                              onBlur={(e) => {
                                const val = e.target.value;
                                if (val && !currentColumns.includes(val) && fk.parentColumn) {
                                  handleAutoCreateFKField(index, val, fk.parentColumn);
                                }
                              }}
                              placeholder="ex: fk_id_pai"
                              className={cn(
                                "h-9 py-0 px-2 text-[13px] font-bold flex-1",
                                !currentColumns.includes(fk.localColumn) && fk.localColumn && "border-destructive/50 bg-destructive/5"
                              )}
                            />
                            <ShPopover>
                              <ShPopoverTrigger asChild>
                                <ShButton variant="outline" size="icon-sm" className="h-9 w-9 border-primary/20 shrink-0">
                                  <ChevronDown className="w-4 h-4 opacity-50" />
                                </ShButton>
                              </ShPopoverTrigger>
                              <ShPopoverContent className="w-64 p-0" align="end">
                                <div className="max-h-60 overflow-y-auto p-1">
                                  {fk.localColumn && !currentColumns.includes(fk.localColumn) && (
                                    <div
                                      className="px-2 py-2 text-xs text-primary font-bold hover:bg-primary/10 cursor-pointer rounded-sm border-b mb-1 flex items-center gap-2"
                                      onClick={() => handleAutoCreateFKField(index, fk.localColumn, fk.parentColumn)}
                                    >
                                      <Plus className="w-3 h-3" />
                                      Criar virtual: {fk.localColumn}
                                    </div>
                                  )}
                                  {currentColumns.length > 0 ? (
                                    currentColumns.map((col) => (
                                      <div
                                        key={col}
                                        className="px-2 py-1.5 text-xs hover:bg-primary/10 cursor-pointer rounded-sm"
                                        onClick={() => handleUpdateForeignKey(index, { localColumn: col })}
                                      >
                                        {col}
                                      </div>
                                    ))
                                  ) : (
                                    <div className="p-2 text-[10px] text-muted-foreground italic">
                                      Nenhuma coluna disponível
                                    </div>
                                  )}
                                </div>
                              </ShPopoverContent>
                            </ShPopover>
                          </div>
                        </div>
                        <div className="flex flex-col items-center justify-center pt-4">
                          <ChevronRight className="w-4 h-4 text-primary opacity-50" />
                        </div>
                        <div className="flex-1 space-y-1">
                          <ShLabel className="text-[9px] text-muted-foreground uppercase">
                            Coluna no Pai
                          </ShLabel>
                          <ShSelect
                            value={fk.parentColumn}
                            onValueChange={(value) =>
                              handleUpdateForeignKey(index, { parentColumn: value })
                            }
                          >
                            {parentKeyColumns.map((col) => (
                              <ShSelectItem key={col} value={col}>
                                {col}
                              </ShSelectItem>
                            ))}
                          </ShSelect>
                        </div>
                        <div className="pt-4">
                          <ShButton
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => handleRemoveForeignKey(index)}
                            className="text-destructive h-8 w-8"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </ShButton>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-[10px] text-muted-foreground italic">
                    Nenhuma chave estrangeira definida para este nodo.
                  </p>
                )}
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
            parentColumns={[
              ...Object.values(node.fieldMappings).map((m) => ({
                column: m.column,
                isKey: m.primaryKey || m.uniqueKey,
                type: m.type,
                nativeType: m.nativeType,
              })),
              ...Object.values(node.customFields).map((f) => ({
                column: f.column,
                isKey: f.primaryKey || f.uniqueKey,
                type: f.type,
                nativeType: f.nativeType,
              })),
            ]}
          />
        ))}
    </div>
  );
};

const FUNCTION_AUTO_TYPE: Record<CustomFunction, CanonicalType> = {
  CURRENT_TIMESTAMP_UTC_3: 'DATETIME',
  CURRENT_DATE_BR: 'DATE',
  UUID_GEN: 'TEXT',
  AUTO_INCREMENT: 'NUMBER',
  STATIC_VALUE: 'TEXT',
};

const FUNCTION_ALLOWED_TYPES: Record<CustomFunction, string[]> = {
  CURRENT_TIMESTAMP_UTC_3: ['DATETIME', 'TEXT', 'DATE'],
  CURRENT_DATE_BR: ['DATE', 'TEXT'],
  UUID_GEN: ['TEXT'],
  AUTO_INCREMENT: ['NUMBER'],
  STATIC_VALUE: ['TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'],
};

const CustomFieldForm = ({
  onAdd,
  adapterTypes,
  open,
  onOpenChange,
}: {
  onAdd: (definition: CustomFieldDefinition) => void;
  adapterTypes?: AdapterTypesResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) => {
  const [columnName, setColumnName] = useState('');
  const [staticValue, setStaticValue] = useState('');
  const [selectedFunction, setSelectedFunction] =
    useState<CustomFunction>('CURRENT_TIMESTAMP_UTC_3');
  const [canonicalType, setCanonicalType] = useState<CanonicalType>('DATETIME');

  return (
    <ShSheet open={open} onOpenChange={onOpenChange}>
      <ShSheetContent side="right" className="sm:max-w-md w-full flex flex-col p-0">
        <ShSheetHeader className="px-6 py-4 border-b">
          <ShSheetTitle>Novo Campo Virtual</ShSheetTitle>
        </ShSheetHeader>
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
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
        <ShSheetFooter className="p-6 border-t bg-muted/20">
          <ShButton
            onClick={() =>
              onAdd({
                column: columnName || 'id',
                type: canonicalType,
                nativeType: selectedFunction === 'AUTO_INCREMENT' ? 'INT NOT NULL AUTO_INCREMENT' : '',
                function: selectedFunction,
                staticValue: selectedFunction === 'STATIC_VALUE' ? staticValue : undefined,
                primaryKey: selectedFunction === 'AUTO_INCREMENT',
                uniqueKey: selectedFunction === 'AUTO_INCREMENT',
              })
            }
            className="w-full"
          >
            Adicionar Campo
          </ShButton>
        </ShSheetFooter>
      </ShSheetContent>
    </ShSheet>
  );
};
