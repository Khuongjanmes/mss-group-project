import { LogOut, Menu } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router';

import { signOut } from '@/api/auth';
import { hasRole, type Session, useSession } from '@/auth/session';
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

/** Link của người đã đăng nhập; admin không mua vé nên chỉ có trang quản trị. */
function accountLinks(session: Session): { to: string; label: string }[] {
  if (hasRole(session, 'ADMIN')) return [{ to: '/admin', label: 'Quản trị' }];
  return [
    { to: '/orders', label: 'Đơn của tôi' },
    { to: '/tickets', label: 'Vé của tôi' },
    { to: '/organizer', label: 'Quản lý sự kiện' },
  ];
}

export default function Layout() {
  const session = useSession();
  const links = session ? accountLinks(session) : [];

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-card">
        <nav className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-bold text-primary">
            OpenEvent
          </Link>
          <NavLink
            to="/events"
            className={({ isActive }) =>
              cn('mr-auto text-sm font-medium transition-colors hover:text-foreground', isActive ? 'text-foreground' : 'text-muted-foreground')
            }
          >
            Sự kiện
          </NavLink>
          {session ? (
            <>
              {/* Màn hình rộng: link nằm ngang. */}
              <div className="hidden items-center gap-4 sm:flex">
                {links.map((link) => (
                  <Button key={link.to} asChild variant="ghost" size="sm">
                    <Link to={link.to}>{link.label}</Link>
                  </Button>
                ))}
                <span className="text-sm text-muted-foreground">{session.user.fullName}</span>
                <Button variant="outline" size="sm" onClick={() => void signOut()}>
                  Đăng xuất
                </Button>
              </div>
              {/* Điện thoại: gom vào một menu để thanh điều hướng không vỡ dòng. */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="icon-sm" className="sm:hidden" aria-label="Mở menu tài khoản">
                    <Menu />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="min-w-56">
                  <DropdownMenuLabel className="grid font-normal leading-tight">
                    <span className="truncate font-medium">{session.user.fullName}</span>
                    <span className="truncate text-xs text-muted-foreground">{session.user.email}</span>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  {links.map((link) => (
                    <DropdownMenuItem key={link.to} asChild>
                      <Link to={link.to}>{link.label}</Link>
                    </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => void signOut()}>
                    <LogOut />
                    Đăng xuất
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <Button asChild size="sm">
              <Link to="/login">Đăng nhập</Link>
            </Button>
          )}
        </nav>
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
