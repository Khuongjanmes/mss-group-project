import { CalendarDays, LayoutDashboard, LogOut, Menu, ReceiptText } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router';

import { signOut } from '@/api/auth';
import { hasRole, type Session, useSession } from '@/auth/session';
import { UserAvatar } from '@/components/common/user';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';
import { NotificationBell } from '@/notifications/NotificationBell';

type NavItem = { title: string; to: string };

/** Menu chính; admin không mua vé nên chỉ có trang quản trị. */
function navFor(session: Session | null): NavItem[] {
  const events = { title: 'Sự kiện', to: '/events' };
  if (!session) return [events];
  if (hasRole(session, 'ADMIN')) return [events, { title: 'Quản trị', to: '/admin' }];
  return [
    events,
    { title: 'Vé của tôi', to: '/tickets' },
    { title: 'Quản lý sự kiện', to: '/organizer' },
  ];
}

/** Header theo StudentHeader của openevent: logo · menu ngang gạch chân cam · tài khoản; dưới `lg` menu gom vào ☰. */
export default function Layout() {
  const session = useSession();
  const nav = navFor(session);

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky -top-px z-40 border-b bg-card">
        <div className="container mx-auto flex h-14 max-w-6xl items-center gap-10 px-4 lg:h-16">
          <Link to="/events" className="flex shrink-0 items-center gap-2" aria-label="Sự kiện">
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground lg:size-9">
              <CalendarDays className="size-4 lg:size-5" aria-hidden />
            </span>
            <span className="text-lg font-bold">OpenEvent</span>
          </Link>

          <nav aria-label="Điều hướng chính" className="hidden h-full flex-1 items-center gap-8 lg:flex">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    'flex h-full items-center border-b-2 text-sm font-medium transition-colors',
                    isActive ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
                  )
                }
              >
                {item.title}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            {session ? (
              <>
                {!hasRole(session, 'ADMIN') && <NotificationBell />}
                <AccountMenu session={session} />
              </>
            ) : (
              <Button asChild variant="ghost">
                <Link to="/login">Đăng nhập</Link>
              </Button>
            )}

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Mở menu">
                  <Menu className="size-5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                {nav.map((item) => (
                  <DropdownMenuItem key={item.to} asChild>
                    <NavLink to={item.to} className={({ isActive }) => cn(isActive && 'font-semibold text-accent-foreground')}>
                      {item.title}
                    </NavLink>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="bg-footer py-6 text-center text-sm text-footer-foreground">
        © {new Date().getFullYear()} OpenEvent
      </footer>
    </div>
  );
}

/** Theo HeaderAccountMenu của openevent: avatar và tên, menu có email, trang quản lý, đăng xuất. */
function AccountMenu({ session }: { session: Session }) {
  const user = { name: session.user.fullName, email: session.user.email, avatarUrl: session.user.avatarUrl ?? undefined };
  const admin = hasRole(session, 'ADMIN');

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-10 gap-2 px-2" aria-label="Tài khoản">
          <UserAvatar user={user} />
          <span className="hidden max-w-40 truncate sm:inline">{user.name}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuLabel className="grid font-normal">
          <span className="truncate font-medium">{user.name}</span>
          <span className="truncate text-xs text-muted-foreground">{user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {!admin && (
          <DropdownMenuItem asChild>
            <Link to="/orders">
              <ReceiptText />
              Đơn của tôi
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem asChild>
          <Link to={admin ? '/admin' : '/organizer'}>
            <LayoutDashboard />
            {admin ? 'Quản trị' : 'Quản lý sự kiện'}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void signOut()}>
          <LogOut />
          Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
