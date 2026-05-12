import { Link } from '@tanstack/react-router';
import { LayoutDashboard, Database, History, Settings } from 'lucide-react';
import { ShButton } from '@/lib/components/sh-button/button.component';

interface ShSidebarProps {
  onNavigate?: () => void;
}

export const ShSidebar = ({ onNavigate }: ShSidebarProps) => {
  return (
    <aside className="w-64 border-r bg-muted/30 flex flex-col h-full">
      <div className="p-6 border-b">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <Database className="w-6 h-6 text-primary" />
          <span>SP Migrator</span>
        </h1>
      </div>

      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        <Link
          to="/"
          activeProps={{ className: 'bg-primary/10 text-primary font-medium' }}
          className="flex items-center gap-3 px-4 py-2 rounded-md transition-colors hover:bg-muted text-sm"
          onClick={onNavigate}
        >
          <LayoutDashboard className="w-4 h-4 shrink-0" />
          <span>Jobs</span>
        </Link>

        <Link
          to="/connections"
          activeProps={{ className: 'bg-primary/10 text-primary font-medium' }}
          className="flex items-center gap-3 px-4 py-2 rounded-md transition-colors hover:bg-muted text-sm"
          onClick={onNavigate}
        >
          <Database className="w-4 h-4 shrink-0" />
          <span>Connections</span>
        </Link>

        <Link
          to="/logs"
          activeProps={{ className: 'bg-primary/10 text-primary font-medium' }}
          className="flex items-center gap-3 px-4 py-2 rounded-md transition-colors hover:bg-muted text-sm"
          onClick={onNavigate}
        >
          <History className="w-4 h-4 shrink-0" />
          <span>Logs</span>
        </Link>
      </nav>

      <div className="p-4 border-t">
        <ShButton variant="ghost" className="w-full justify-start gap-3 text-sm">
          <Settings className="w-4 h-4 shrink-0" />
          <span>Configurações</span>
        </ShButton>
      </div>
    </aside>
  );
};
