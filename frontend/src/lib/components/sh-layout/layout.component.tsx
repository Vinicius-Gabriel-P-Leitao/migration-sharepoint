import { useState } from 'react';
import { Outlet } from '@tanstack/react-router';
import { Menu, Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import { ShSidebar } from '@/lib/components/sh-sidebar/sidebar.component';
import { ShButton } from '@/lib/components/sh-button/button.component';
import {
  Drawer,
  DrawerContent,
} from '@/lib/components/ui/drawer';

export const ShLayoutComponent = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { theme, setTheme } = useTheme();

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Desktop sidebar */}
      <div className="hidden md:block">
        <ShSidebar />
      </div>

      {/* Mobile sidebar via Drawer */}
      <Drawer open={sidebarOpen} onOpenChange={setSidebarOpen} direction="left">
        <DrawerContent className="p-0 rounded-none">
          <ShSidebar onNavigate={() => setSidebarOpen(false)} />
        </DrawerContent>
      </Drawer>

      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="h-14 border-b flex items-center px-4 gap-2 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
          <ShButton
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label="Abrir menu"
          >
            <Menu className="w-5 h-5" />
          </ShButton>

          <div className="flex-1" />

          <ShButton
            variant="ghost"
            size="icon"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            aria-label="Alternar tema"
          >
            <Sun className="w-4 h-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
            <Moon className="absolute w-4 h-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
          </ShButton>
        </header>

        <div className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
};
