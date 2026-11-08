import { useState, type ReactNode } from 'react';
import { Outlet } from 'react-router';
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar';
import { AppSidebar, type NavGroup } from '@/components/common/navigation';
import { AppTopbar } from './AppTopbar';

type DashboardLayoutProps = {
  nav: NavGroup[];
  homeTo: string;
  subtitle: string;
  topbarActions?: ReactNode;
  /** Usually a `SidebarUserMenu`. */
  sidebarFooter?: ReactNode;
};

/** Sidebar + topbar shell for the SRO area (and later the CTV area). */
export function DashboardLayout({
  nav,
  homeTo,
  subtitle,
  topbarActions,
  sidebarFooter,
}: DashboardLayoutProps) {
  // Read once on mount; SidebarProvider only uses defaultOpen initially.
  const [defaultOpen] = useState(readSidebarOpen);

  return (
    <SidebarProvider defaultOpen={defaultOpen}>
      <AppSidebar nav={nav} homeTo={homeTo} subtitle={subtitle} footer={sidebarFooter} />
      {/* SidebarInset already renders the page's <main> landmark.
          min-w-0 lets it shrink below wide content (tables) so that content scrolls, not the page. */}
      <SidebarInset className="min-w-0">
        <AppTopbar actions={topbarActions} />
        <div className="flex-1 p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}

// SidebarProvider persists the open state in this cookie; restore it on reload.
// Falls back to open where there is no document (SSR, prerender).
function readSidebarOpen() {
  if (typeof document === 'undefined') return true;
  return !document.cookie.split('; ').includes('sidebar_state=false');
}
