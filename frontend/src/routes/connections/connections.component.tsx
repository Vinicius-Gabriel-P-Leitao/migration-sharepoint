import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
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
  ShDialog,
  ShDialogContent,
  ShDialogHeader,
  ShDialogTitle,
  ShDialogFooter,
} from '@lib/components/sh-dialog/dialog.component';
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
import { ShInput } from '@lib/components/sh-input/input.component';
import { ShLabel } from '@lib/components/sh-label/label.component';
import {
  ShTooltip,
  ShTooltipContent,
  ShTooltipTrigger,
} from '@lib/components/sh-tooltip/tooltip.component';
import {
  useConnections,
  useCreateConnection,
  useDeleteConnection,
} from '@lib/hooks/connections.hook';
import type { ConnectionSummary } from '@lib/services/connections.service';
import { Plus, Trash2, Database, Loader2, Key } from 'lucide-react';
import { toast } from 'sonner';

const connectionSchema = z.object({
  key: z
    .string()
    .min(1, 'Key obrigatória')
    .regex(/^[A-Z0-9_]+$/, 'Apenas letras maiúsculas, números e underscores'),
  name: z.string().min(1, 'Nome obrigatório'),
  url: z.string().min(1, 'URL obrigatória'),
});

type ConnectionFormData = z.infer<typeof connectionSchema>;

export const ConnectionsRoute = () => {
  const [addOpen, setAddOpen] = useState(false);
  const [keyToDelete, setKeyToDelete] = useState<string | null>(null);

  const { data: connections, isLoading } = useConnections();

  const createMutation = useCreateConnection();
  const deleteMutation = useDeleteConnection();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ConnectionFormData>({
    resolver: zodResolver(connectionSchema),
  });

  const onSubmit = (data: ConnectionFormData) =>
    createMutation.mutate(data, {
      onSuccess: () => {
        toast.success('Conexão registrada com sucesso');
        setAddOpen(false);
        reset();
      },
      onError: () => toast.error('Erro ao registrar conexão'),
    });

  const handleDelete = () => {
    if (keyToDelete) {
      deleteMutation.mutate(keyToDelete, {
        onSuccess: () => {
          toast.success('Conexão removida');
        },
        onError: () => toast.error('Erro ao remover conexão'),
      });
    }
    setKeyToDelete(null);
  };

  return (
    <>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">Connections</h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Credenciais de banco de dados armazenadas em memória.
            </p>
          </div>
          <ShButton onClick={() => setAddOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            Nova Conexão
          </ShButton>
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <ShCard key={i}>
                <ShCardHeader>
                  <ShSkeleton className="h-5 w-32" />
                  <ShSkeleton className="h-4 w-24 mt-1" />
                </ShCardHeader>
                <ShCardFooter className="border-t pt-3">
                  <ShSkeleton className="h-8 w-8 ml-auto" />
                </ShCardFooter>
              </ShCard>
            ))}
          </div>
        )}

        {/* Empty state */}
        {!isLoading && connections?.length === 0 && (
          <ShCard className="border-dashed">
            <ShCardContent className="py-16 flex flex-col items-center gap-4 text-muted-foreground">
              <Database className="w-12 h-12 opacity-20" />
              <div className="text-center">
                <p className="font-medium text-foreground">Nenhuma conexão registrada</p>
                <p className="text-sm mt-1">
                  Registre a connection string do banco de destino para criar jobs.
                </p>
              </div>
              <ShButton onClick={() => setAddOpen(true)}>
                <Plus className="w-4 h-4 mr-2" />
                Adicionar conexão
              </ShButton>
            </ShCardContent>
          </ShCard>
        )}

        {/* Connection cards */}
        {!isLoading && connections && connections.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {connections.map((conn: ConnectionSummary) => (
              <ShCard key={conn.key} className="flex flex-col">
                <ShCardHeader className="pb-3">
                  <div className="flex items-start gap-2">
                    <div className="p-2 rounded-md bg-primary/10 shrink-0">
                      <Database className="w-4 h-4 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <ShCardTitle className="text-sm font-mono truncate">{conn.key}</ShCardTitle>
                      <ShCardDescription className="mt-0.5 truncate">{conn.name}</ShCardDescription>
                    </div>
                  </div>
                </ShCardHeader>

                <ShCardFooter className="border-t pt-3 mt-auto">
                  <div className="flex items-center gap-1.5 w-full">
                    <div className="flex items-center gap-1 text-xs text-muted-foreground">
                      <Key className="w-3 h-3" />
                      URL protegida
                    </div>
                    <div className="ml-auto">
                      <ShTooltip>
                        <ShTooltipTrigger asChild>
                          <ShButton
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => setKeyToDelete(conn.key)}
                            disabled={deleteMutation.isPending}
                            className="text-destructive hover:text-destructive"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </ShButton>
                        </ShTooltipTrigger>
                        <ShTooltipContent>Remover conexão</ShTooltipContent>
                      </ShTooltip>
                    </div>
                  </div>
                </ShCardFooter>
              </ShCard>
            ))}
          </div>
        )}
      </div>

      {/* Add connection dialog */}
      <ShDialog
        open={addOpen}
        onOpenChange={(open) => {
          setAddOpen(open);
          if (!open) reset();
        }}
      >
        <ShDialogContent className="sm:max-w-md">
          <ShDialogHeader>
            <ShDialogTitle>Nova Conexão</ShDialogTitle>
          </ShDialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <ShLabel htmlFor="conn-key">Chave</ShLabel>
              <ShInput
                id="conn-key"
                placeholder="Ex: MYSQL_PROD"
                {...register('key')}
                aria-invalid={!!errors.key}
              />
              {errors.key && <p className="text-xs text-destructive">{errors.key.message}</p>}
            </div>
            <div className="space-y-1.5">
              <ShLabel htmlFor="conn-name">Nome Amigável</ShLabel>
              <ShInput
                id="conn-name"
                placeholder="Ex: MySQL Produção"
                {...register('name')}
                aria-invalid={!!errors.name}
              />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>
            <div className="space-y-1.5">
              <ShLabel htmlFor="conn-url">JDBC URL / Mongo URI</ShLabel>
              <ShInput
                id="conn-url"
                type="password"
                placeholder="jdbc:mysql://host:3306/db"
                {...register('url')}
                aria-invalid={!!errors.url}
              />
              {errors.url && <p className="text-xs text-destructive">{errors.url.message}</p>}
            </div>
            <ShDialogFooter>
              <ShButton
                type="button"
                variant="outline"
                onClick={() => {
                  setAddOpen(false);
                  reset();
                }}
              >
                Cancelar
              </ShButton>
              <ShButton type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Salvar
              </ShButton>
            </ShDialogFooter>
          </form>
        </ShDialogContent>
      </ShDialog>

      {/* Delete confirmation */}
      <ShAlertDialog
        open={keyToDelete !== null}
        onOpenChange={(open) => !open && setKeyToDelete(null)}
      >
        <ShAlertDialogContent size="sm">
          <ShAlertDialogHeader>
            <ShAlertDialogTitle>Remover conexão?</ShAlertDialogTitle>
            <ShAlertDialogDescription>
              A conexão <span className="font-mono font-semibold">{keyToDelete}</span> será removida
              da memória. Jobs que a referenciam falharão na próxima execução.
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
