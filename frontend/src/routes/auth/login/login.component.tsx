import { ShButton } from '@lib/components/sh-button/button.component';
import {
  ShInputGroup,
  ShInputGroupAddon,
  ShInputGroupInput,
  ShInputGroupText,
  ShInputGroupButton,
} from '@lib/components/sh-input-group/input-group.component';
import {
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@lib/components/sh-form/form.component';
import { Field, FieldContent } from '@lib/components/sh-field/field.component';
import { getErrorMessage } from '@lib/utils/api-error.util';
import { useState } from 'react';
import { useForm, FormProvider, type ControllerRenderProps } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Eye, EyeOff, Loader2, Lock, LogIn, Mail } from 'lucide-react';
import { toast } from 'sonner';
import { loginSchema, type LoginFormData } from '../auth.schema';
import { loginAttempt } from '../services/auth.service';

export function LoginPage() {
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);

  const loginMutation = useMutation({
    mutationFn: loginAttempt,
    onSuccess: (data) => {
      if (data.session.passwordResetRequired) {
        toast.error('Você deve alterar sua senha antes de continuar.', { icon: '🔑' });
        void navigate({ to: '/reset-password' });
      } else {
        if (data.user.roles.includes('ROLE_ADMIN')) {
          toast.success(`Bem-vindo, ${data.user.profile.username}!`);
        }

        void navigate({ to: '/' });
      }
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Credenciais inválidas. Tente novamente.'));
    },
  });

  const methods = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (values: LoginFormData) => {
    await loginMutation.mutateAsync(values);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4 relative overflow-hidden">
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-primary/20 rounded-full mix-blend-multiply filter blur-3xl opacity-70 animate-blob"></div>
      <div className="absolute top-[20%] right-[-10%] w-[40%] h-[40%] bg-primary/15 rounded-full mix-blend-multiply filter blur-3xl opacity-70 animate-blob animation-delay-2000"></div>
      <div className="absolute bottom-[-10%] left-[20%] w-[40%] h-[40%] bg-primary/10 rounded-full mix-blend-multiply filter blur-3xl opacity-70 animate-blob animation-delay-4000"></div>

      <div className="w-full max-w-md bg-card/70 backdrop-blur-xl border border-border/50 shadow-2xl rounded-3xl p-8 relative z-10">
        <div className="text-center mb-10">
          <div className="mx-auto bg-primary/15 w-16 h-16 rounded-2xl flex items-center justify-center shadow-inner mb-4">
            <LogIn className="w-8 h-8 text-primary" />
          </div>
          <h1 className="text-3xl font-extrabold text-foreground tracking-tight">Migração</h1>
          <p className="text-muted-foreground mt-2 text-sm">
            Login para migrar dados do sharepoint
          </p>
        </div>

        <FormProvider {...methods}>
          <form onSubmit={methods.handleSubmit(onSubmit)} className="space-y-6">
            <FormField
              control={methods.control}
              name="email"
              render={({ field }: { field: ControllerRenderProps<LoginFormData, 'email'> }) => (
                <FormItem>
                  <Field>
                    <FormLabel>E-mail</FormLabel>
                    <FieldContent>
                      <ShInputGroup>
                        <ShInputGroupAddon>
                          <ShInputGroupText>
                            <Mail className="size-4" />
                          </ShInputGroupText>
                        </ShInputGroupAddon>
                        <ShInputGroupInput
                          {...field}
                          type="email"
                          placeholder="admin@exemplo.com"
                        />
                      </ShInputGroup>
                    </FieldContent>
                    <FormMessage />
                  </Field>
                </FormItem>
              )}
            />

            <FormField
              control={methods.control}
              name="password"
              render={({ field }: { field: ControllerRenderProps<LoginFormData, 'password'> }) => (
                <FormItem>
                  <Field>
                    <FormLabel>Senha</FormLabel>
                    <FieldContent>
                      <ShInputGroup>
                        <ShInputGroupAddon>
                          <ShInputGroupText>
                            <Lock className="size-4" />
                          </ShInputGroupText>
                        </ShInputGroupAddon>
                        <ShInputGroupInput
                          {...field}
                          type={showPassword ? 'text' : 'password'}
                          placeholder="••••••••"
                        />
                        <ShInputGroupAddon align="inline-end">
                          <ShInputGroupButton onClick={() => setShowPassword((value) => !value)}>
                            {showPassword ? (
                              <EyeOff className="size-4" />
                            ) : (
                              <Eye className="size-4" />
                            )}
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
              className="w-full h-12 text-lg shadow-primary/25"
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Entrar
            </ShButton>
          </form>
        </FormProvider>
      </div>
    </div>
  );
}
