import { useState, useEffect } from "react";
import { useResolveSharePoint } from "@lib/hooks/sharepoint.hook";
import { useAdapterTypes } from "@lib/hooks/adapters.hook";
import type {
  JobNode,
  FieldMapping,
  CanonicalType,
  TargetDb,
} from "../jobs.type";
import {
  ShCard,
  ShCardContent,
  ShCardHeader,
  ShCardTitle,
} from "@lib/components/sh-card/card.component";
import { ShInput } from "@lib/components/sh-input/input.component";
import { ShLabel } from "@lib/components/sh-label/label.component";
import { ShButton } from "@lib/components/sh-button/button.component";
import {
  ShSelect,
  ShSelectItem,
} from "@lib/components/sh-select/select.component";
import {
  ShPopover,
  ShPopoverTrigger,
  ShPopoverContent,
} from "@lib/components/sh-popover/popover.component";
import {
  ShTable,
  ShTableHeader,
  ShTableRow,
  ShTableHead,
  ShTableBody,
  ShTableCell,
} from "@lib/components/sh-table/table.component";
import {
  Loader2,
  Plus,
  Trash2,
  ChevronRight,
  ChevronDown,
  Settings2,
  CheckCircle2,
} from "lucide-react";
import { toast } from "sonner";
import { cn } from "@lib/utils/cn.util";

const NONE_NATIVE = "__none__";

const parseNativeType = (fullType: string) => {
  const match = fullType.match(/^([^(]+)(?:\((.*)\))?$/);
  if (!match) return { base: fullType, params: [] as string[] };
  const base = match[1].trim();
  const params = match[2]
    ? match[2].split(",").map((param) => param.trim())
    : [];
  return { base, params };
};

const formatNativeType = (base: string, params: string[]) => {
  const filteredParams = params.map((param) => param.trim());
  if (
    filteredParams.length === 0 ||
    filteredParams.every((param) => param === "")
  )
    return base;
  return `${base}(${filteredParams.join(",")})`;
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
  const [sharepointUrl, setSharepointUrl] = useState(node.sharepointUrl || "");
  const [isExpanded, setIsExpanded] = useState(true);
  const [availableColumns, setAvailableColumns] = useState<string[]>([]);

  const resolveMutation = useResolveSharePoint();
  const { data: adapterTypes } = useAdapterTypes(targetDb);

  // Sync internal state when node.sharepointUrl changes from outside (e.g. during initial edit load)
  useEffect(() => {
    if (node.sharepointUrl && sharepointUrl === "") {
      setSharepointUrl(node.sharepointUrl);
    }
  }, [node.sharepointUrl]);

  // Initialize available columns from existing mappings if any
  useEffect(() => {
    if (
      Object.keys(node.fieldMappings).length > 0 &&
      availableColumns.length === 0
    ) {
      const keys = Object.keys(node.fieldMappings);
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
          column: columnName.toLowerCase().replace(/[\s-]+/g, "_"),
          type: "TEXT",
          nativeType: "",
        };
      });

      onChange({
        ...node,
        sharepointUrl,
        siteId: data.siteId,
        listId: data.listId,
        fieldMappings: newMappings,
      });
      toast.success("Lista resolvida com sucesso");
    } catch {
      toast.error("Erro ao resolver URL");
    }
  };

  const handleUpdateMapping = (
    spColumn: string,
    updates: Partial<FieldMapping>,
  ) => {
    const newMappings = { ...node.fieldMappings };
    newMappings[spColumn] = { ...newMappings[spColumn], ...updates };
    onChange({ ...node, fieldMappings: newMappings });
  };

  const handleToggleMapping = (spColumn: string, included: boolean) => {
    const newMappings = { ...node.fieldMappings };
    if (included) {
      newMappings[spColumn] = {
        column: spColumn.toLowerCase().replace(/[\s-]+/g, "_"),
        type: "TEXT",
        nativeType: "",
      };
    } else {
      delete newMappings[spColumn];
    }
    onChange({ ...node, fieldMappings: newMappings });
  };

  const handleAddChild = () => {
    const newNode: JobNode = {
      siteId: "",
      listId: "",
      tableName: "",
      fieldMappings: {},
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
    <div
      className={cn(
        "space-y-4",
        !isRoot && "pl-6 border-l-2 border-muted ml-2 mt-4",
      )}
    >
      <ShCard>
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
              {isRoot
                ? "Raiz da Migração"
                : `Nodo: ${node.tableName || "Novo"}`}
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
              onClick={handleAddChild}
              className="h-8"
            >
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
                    disabled={
                      resolveMutation.isPending || !sharepointUrl.trim()
                    }
                    className="h-8"
                  >
                    {resolveMutation.isPending ? (
                      <Loader2 className="w-3 h-3 animate-spin" />
                    ) : (
                      "Resolver"
                    )}
                  </ShButton>
                </div>
              </div>
              <div className="space-y-1.5">
                <ShLabel>Tabela Destino</ShLabel>
                <ShInput
                  placeholder="Ex: tb_clientes"
                  value={node.tableName}
                  onChange={(event) =>
                    onChange({ ...node, tableName: event.target.value })
                  }
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

            {availableColumns.length > 0 && (
              <div className="rounded-md border overflow-hidden">
                <ShTable>
                  <ShTableHeader className="bg-muted/50">
                    <ShTableRow>
                      <ShTableHead className="w-10 h-8 px-3">
                        <input
                          type="checkbox"
                          checked={availableColumns.every(
                            (columnName) => !!node.fieldMappings[columnName],
                          )}
                          onChange={(event) => {
                            const allMappings = availableColumns.reduce(
                              (accumulator, columnName) => {
                                if (event.target.checked) {
                                  accumulator[columnName] = node.fieldMappings[
                                    columnName
                                  ] || {
                                    column: columnName
                                      .toLowerCase()
                                      .replace(/[\s-]+/g, "_"),
                                    type: "TEXT",
                                    nativeType: "",
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
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Campo SP
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Coluna
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Tipo Canônico
                      </ShTableHead>
                      <ShTableHead className="h-8 text-[10px] uppercase font-bold px-3">
                        Tipo Nativo
                      </ShTableHead>
                    </ShTableRow>
                  </ShTableHeader>
                  <ShTableBody>
                    {availableColumns.map((spColumn) => {
                      const mapping = node.fieldMappings[spColumn];
                      const included = !!mapping;
                      const { base: nativeBase, params: nativeParams } =
                        parseNativeType(mapping?.nativeType || "");
                      const nativeDefinition = adapterTypes?.nativeTypes.find(
                        (typeDef) => typeDef.name === nativeBase,
                      );

                      return (
                        <ShTableRow
                          key={spColumn}
                          className={cn(!included && "opacity-40")}
                        >
                          <ShTableCell className="py-1.5 px-3">
                            <input
                              type="checkbox"
                              checked={included}
                              onChange={(event) =>
                                handleToggleMapping(
                                  spColumn,
                                  event.target.checked,
                                )
                              }
                            />
                          </ShTableCell>
                          <ShTableCell className="py-1.5 px-3">
                            <div className="font-mono text-[12px] leading-none">
                              {spColumn}
                            </div>

                            {included && (
                              <div className="text-[9px] text-muted-foreground mt-1 flex items-center gap-1">
                                <span>→</span>
                                <span className="font-bold text-primary">
                                  {mapping.nativeType ||
                                    adapterTypes?.canonical[mapping.type] ||
                                    "..."}
                                </span>
                              </div>
                            )}
                          </ShTableCell>

                          <ShTableCell className="py-1.5 px-3">
                            <ShInput
                              value={mapping?.column || ""}
                              onChange={(event) =>
                                handleUpdateMapping(spColumn, {
                                  column: event.target.value,
                                })
                              }
                              disabled={!included}
                              className="h-10 py-0 px-2"
                            />
                          </ShTableCell>

                          <ShTableCell className="py-1.5 px-3">
                            <ShSelect
                              value={mapping?.type || "TEXT"}
                              onValueChange={(value) =>
                                handleUpdateMapping(spColumn, {
                                  type: value as CanonicalType,
                                })
                              }
                              disabled={!included}
                              size="sm"
                            >
                              {Object.keys(adapterTypes?.canonical || {}).map(
                                (typeName) => (
                                  <ShSelectItem
                                    key={typeName}
                                    value={typeName}
                                    className="text-[10px]"
                                  >
                                    {typeName}
                                  </ShSelectItem>
                                ),
                              )}
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
                                    "h-7 w-full justify-start font-mono text-[10px] px-2",
                                    !mapping?.nativeType &&
                                      "text-muted-foreground italic",
                                  )}
                                >
                                  <Settings2 className="w-3 h-3 mr-1.5 opacity-50" />
                                  {mapping?.nativeType || "(canônico)"}
                                </ShButton>
                              </ShPopoverTrigger>
                              <ShPopoverContent
                                className="w-80 p-4 space-y-4"
                                side="left"
                              >
                                <div className="space-y-1.5">
                                  <ShLabel className="text-[11px] font-bold">
                                    Tipo Base
                                  </ShLabel>
                                  <ShSelect
                                    value={nativeBase || NONE_NATIVE}
                                    onValueChange={(value) =>
                                      handleUpdateMapping(spColumn, {
                                        nativeType:
                                          value === NONE_NATIVE ? "" : value,
                                      })
                                    }
                                  >
                                    <ShSelectItem
                                      value={NONE_NATIVE}
                                      className="text-xs text-muted-foreground"
                                    >
                                      (Usar Tipo Canônico)
                                    </ShSelectItem>
                                    {adapterTypes?.nativeTypes.map(
                                      (typeDef) => (
                                        <ShSelectItem
                                          key={typeDef.name}
                                          value={typeDef.name}
                                          className="text-xs"
                                        >
                                          {typeDef.name}
                                        </ShSelectItem>
                                      ),
                                    )}
                                  </ShSelect>
                                </div>

                                {nativeDefinition &&
                                  nativeDefinition.params.length > 0 && (
                                    <div className="space-y-3 pt-2 border-t">
                                      <ShLabel className="text-[11px] font-bold">
                                        Parâmetros do Tipo
                                      </ShLabel>
                                      <div className="grid grid-cols-2 gap-3">
                                        {nativeDefinition.params.map(
                                          (paramSpec, paramIndex) => {
                                            const effectiveMax = paramSpec.max;

                                            return (
                                              <div
                                                key={paramIndex}
                                                className="space-y-1"
                                              >
                                                <ShLabel className="text-[10px] text-muted-foreground uppercase">
                                                  {paramSpec.label} (min:{" "}
                                                  {paramSpec.min}, max:{" "}
                                                  {effectiveMax})
                                                </ShLabel>
                                                <ShInput
                                                  type="number"
                                                  value={
                                                    nativeParams[paramIndex] ||
                                                    ""
                                                  }
                                                  onChange={(event) => {
                                                    const numericValue =
                                                      parseInt(
                                                        event.target.value,
                                                      );
                                                    if (isNaN(numericValue))
                                                      return;

                                                    // Enforce range
                                                    const clampedValue =
                                                      Math.max(
                                                        paramSpec.min,
                                                        Math.min(
                                                          effectiveMax,
                                                          numericValue,
                                                        ),
                                                      );

                                                    const updatedParams = [
                                                      ...nativeParams,
                                                    ];
                                                    while (
                                                      updatedParams.length <
                                                      nativeDefinition.params
                                                        .length
                                                    )
                                                      updatedParams.push("");
                                                    updatedParams[paramIndex] =
                                                      String(clampedValue);
                                                    handleUpdateMapping(
                                                      spColumn,
                                                      {
                                                        nativeType:
                                                          formatNativeType(
                                                            nativeBase,
                                                            updatedParams,
                                                          ),
                                                      },
                                                    );
                                                  }}
                                                  className="h-8 text-xs"
                                                  placeholder={`${paramSpec.min}-${effectiveMax}`}
                                                />
                                              </div>
                                            );
                                          },
                                        )}
                                      </div>
                                      <p className="text-[9px] text-muted-foreground italic">
                                        Valores permitidos conforme metadados e
                                        restrições de negócio.
                                      </p>
                                    </div>
                                  )}
                              </ShPopoverContent>
                            </ShPopover>
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
        node.children.map((child, idx) => (
          <MigrationNode
            key={idx}
            node={child}
            targetDb={targetDb}
            onChange={(updated) => handleUpdateChild(idx, updated)}
            onRemove={() => handleRemoveChild(idx)}
          />
        ))}
    </div>
  );
};
