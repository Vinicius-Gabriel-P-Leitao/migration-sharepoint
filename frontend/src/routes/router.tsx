import { createRouter, createRoute, createRootRoute, Outlet } from '@tanstack/react-router';
import { AppProvider } from '@lib/app.provider';
import { ShLayoutComponent } from '@lib/components/sh-layout/layout.component';
import { JobsRoute } from './jobs/jobs.component';
import { ConnectionsRoute } from './connections/connections.component';
import { LogsRoute } from './logs/logs.component';

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

// Layout Route
const layoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'layout',
  component: ShLayoutComponent,
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
  layoutRoute.addChildren([homeRoute, connectionsRoute, logsRoute]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
