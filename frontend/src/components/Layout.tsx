import { Link, Outlet } from 'react-router';

import { signOut } from '@/api/auth';
import { hasRole, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';

export default function Layout() {
  const session = useSession();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-card">
        <nav className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
          <Link to="/" className="mr-auto text-lg font-bold text-primary">
            OpenEvent
          </Link>
          {session ? (
            <>
              {hasRole(session, 'ADMIN') && (
                <Button asChild variant="ghost" size="sm">
                  <Link to="/admin">Quản trị</Link>
                </Button>
              )}
              {hasRole(session, 'ORGANIZER') && (
                <Button asChild variant="ghost" size="sm">
                  <Link to="/organizer">Quản lý sự kiện</Link>
                </Button>
              )}
              <span className="hidden text-sm text-muted-foreground sm:inline">{session.user.fullName}</span>
              <Button variant="outline" size="sm" onClick={() => void signOut()}>
                Đăng xuất
              </Button>
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
