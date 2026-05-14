import { createRouter, createRoute, createRootRoute, Outlet, redirect } from '@tanstack/react-router';
import { AppProvider } from '@lib/app.provider';
import { ShLayoutComponent } from '@lib/components/sh-layout/layout.component';
import { JobsRoute } from './jobs/jobs.component';
import { ConnectionsRoute } from './connections/connections.component';
import { LogsRoute } from './logs/logs.component';
import { LoginPage } from './auth/login/login.component';
import { ResetPasswordPage } from './auth/reset-password/reset-password.component';
import { useAuthStore } from '@lib/store/auth.store';

// Root Route
const rootRoute = createRootRoute({
  component: () => (
    <AppProvider>
      <div className="min-h-screen bg-background text-foreground font-sans antialiased">
        <Outlet />
      </div>
    </AppProvider>
  ),
});

// Auth Routes (Public)
const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
  beforeLoad: () => {
    const { isAuthenticated, passwordResetRequired } = useAuthStore.getState();
    if (isAuthenticated) {
      if (passwordResetRequired) throw redirect({ to: '/reset-password' });
      throw redirect({ to: '/' });
    }
  },
});

const resetPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/reset-password',
  component: ResetPasswordPage,
});

// Layout Route (Protected)
const layoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'layout',
  component: ShLayoutComponent,
  beforeLoad: () => {
    const { isAuthenticated, passwordResetRequired } = useAuthStore.getState();
    if (!isAuthenticated) {
      throw redirect({ to: '/login' });
    }
    if (passwordResetRequired) {
      throw redirect({ to: '/reset-password' });
    }
  },
});

// Home (Jobs) Route
const homeRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: '/',
  component: JobsRoute,
});

// Connections Route
const connectionsRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: '/connections',
  component: ConnectionsRoute,
});

// Logs Route
const logsRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: '/logs',
  component: LogsRoute,
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  resetPasswordRoute,
  layoutRoute.addChildren([homeRoute, connectionsRoute, logsRoute]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
