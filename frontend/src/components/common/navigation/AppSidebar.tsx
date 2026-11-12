import type { ReactNode } from 'react';
import { CalendarDays } from 'lucide-react';
import { Link, useMatch } from 'react-router';
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar';
import type { NavGroup, NavItem } from './types';

type AppSidebarProps = {
  nav: NavGroup[];
  /** Target of the logo link, usually the dashboard of the area. */
  homeTo: string;
  /** Small caption under the logo, e.g. "Quản trị SRO". */
  subtitle: string;
  footer?: ReactNode;
};

export function AppSidebar({ nav, homeTo, subtitle, footer }: AppSidebarProps) {
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link to={homeTo}>
                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                  <CalendarDays className="size-4" />
                </div>
                <div className="grid flex-1 text-left leading-tight">
                  <span className="truncate font-bold">OpenEvent</span>
                  <span className="truncate text-xs text-muted-foreground">{subtitle}</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {nav.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => (
                  <SidebarNavItem key={item.to} item={item} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      {footer ? <SidebarFooter>{footer}</SidebarFooter> : null}
      <SidebarRail />
    </Sidebar>
  );
}

function SidebarNavItem({ item }: { item: NavItem }) {
  const isActive = useMatch({ path: item.to, end: item.end ?? false }) !== null;
  const Icon = item.icon;

  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={isActive} tooltip={item.title}>
        <Link to={item.to} aria-current={isActive ? 'page' : undefined}>
          <Icon />
          <span>{item.title}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}
