import { ShButton } from "@lib/components/sh-button/button.component";
import {
  ShInputGroup,
  ShInputGroupAddon,
  ShInputGroupInput,
  ShInputGroupButton,
} from "@lib/components/sh-input-group/input-group.component";
import { FormField, FormItem, FormLabel, FormMessage } from "@lib/components/sh-form/form.component";
import { Field, FieldContent } from "@lib/components/sh-field/field.component";
import { getErrorMessage } from "@lib/utils/api-error.util";
import { useAuthStore } from "@lib/store/auth.store";
import { useState } from "react";
import { useForm, FormProvider, type ControllerRenderProps } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Navigate } from "@tanstack/react-router";
import { Eye, EyeOff, KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { firstChangeSchema, type FirstChangeFormData } from "../auth.schema";
import { firstChangePasswordAttempt } from "../services/auth.service";

export function ResetPasswordPage() {
  const { isAuthenticated, passwordResetRequired, user } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const mutation = useMutation({
    mutationFn: (data: FirstChangeFormData) => firstChangePasswordAttempt(data.password),
    onSuccess: () => {
      toast.success("Senha atualizada com sucesso! Por favor, faça login com sua nova senha.");
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Erro ao atualizar senha. Tente novamente."));
    },
  });

  const methods = useForm<FirstChangeFormData>({
    resolver: zodResolver(firstChangeSchema),
    mode: "onChange",
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (values: FirstChangeFormData) => {
    mutation.mutate(values);
  };

  if (!isAuthenticated) return <Navigate to="/login" />;
  if (!passwordResetRequired) return <Navigate to="/" />;

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-6 selection:bg-primary/20 italic-selection">
      <div className="w-full max-w-md">
        <div className="bg-card rounded-3xl shadow-xl border border-border overflow-hidden transform transition-all duration-500 hover:shadow-2xl">
          <div className="p-8 sm:p-10">
            <div className="flex flex-col items-center mb-8 text-center">
              <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center mb-5 animate-pulse-subtle">
                <KeyRound className="w-8 h-8 text-primary" />
              </div>

              <h1 className="text-3xl font-bold text-foreground tracking-tight mb-2">Definir Nova Senha</h1>

              <p className="text-muted-foreground">
                Olá, <span className="font-semibold text-foreground">{user?.profile.username}</span>.
                <br />
                Para sua segurança, você deve definir uma nova senha personalizada.
              </p>
            </div>

            <FormProvider {...methods}>
              <form onSubmit={methods.handleSubmit(onSubmit)} className="space-y-6">
                <FormField
                  control={methods.control}
                  name="password"
                  render={({ field }: { field: ControllerRenderProps<FirstChangeFormData, "password"> }) => (
                    <FormItem>
                      <Field>
                        <FormLabel>Nova Senha</FormLabel>
                        <FieldContent>
                          <ShInputGroup>
                            <ShInputGroupInput {...field} type={showPassword ? "text" : "password"} placeholder="••••••••" />
                            <ShInputGroupAddon align="inline-end">
                              <ShInputGroupButton
                                onClick={() => setShowPassword((value) => !value)}
                              >
                                {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                              </ShInputGroupButton>
                            </ShInputGroupAddon>
                          </ShInputGroup>
                        </FieldContent>
                        <FormMessage />
                      </Field>
                    </FormItem>
                  )}
                />

                <FormField
                  control={methods.control}
                  name="confirmPassword"
                  render={({ field }: { field: ControllerRenderProps<FirstChangeFormData, "confirmPassword"> }) => (
                    <FormItem>
                      <Field>
                        <FormLabel>Confirmar Nova Senha</FormLabel>
                        <FieldContent>
                          <ShInputGroup>
                            <ShInputGroupInput {...field} type={showConfirmPassword ? "text" : "password"} placeholder="••••••••" />
                            <ShInputGroupAddon align="inline-end">
                              <ShInputGroupButton
                                onClick={() => setShowConfirmPassword((v) => !v)}
                              >
                                {showConfirmPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                              </ShInputGroupButton>
                            </ShInputGroupAddon>
                          </ShInputGroup>
                        </FieldContent>
                        <FormMessage />
                      </Field>
                    </FormItem>
                  )}
                />

                <ShButton
                  type="submit"
                  className="w-full h-12 rounded-xl text-lg font-bold shadow-lg shadow-primary/20 transition-all duration-300 hover:scale-[1.02] active:scale-95 disabled:hover:scale-100"
                  disabled={!methods.formState.isValid || methods.formState.isSubmitting || mutation.isPending}
                >
                  {methods.formState.isSubmitting || mutation.isPending ? (
                    <Loader2 className="w-5 h-5 animate-spin mr-2" />
                  ) : (
                    <ShieldCheck className="w-5 h-5 mr-2" />
                  )}

                  {methods.formState.isSubmitting || mutation.isPending ? "Atualizando..." : "Atualizar e Sair"}
                </ShButton>
              </form>
            </FormProvider>
          </div>

          <div className="p-6 bg-muted border-t border-border text-center">
            <p className="text-xs text-muted-foreground font-medium tracking-wide flex items-center justify-center gap-2">
              Acesso Protegido • Sistema de Autenticação • 2025
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
