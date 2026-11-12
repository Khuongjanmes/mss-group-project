import { ArrowLeft, CalendarDays } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';

import { login } from '@/api/auth';
import { errorMessage } from '@/api/client';
import { homePathFor, safeRedirect, setSession, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

/** Vòng tròn mờ trên nền cam, theo LoginPage của openevent; từ `sm` có thêm vài vòng. */
function Decorations() {
  return (
    <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
      <div className="absolute -top-32 -right-24 size-80 rounded-full bg-primary-foreground/10 sm:size-112" />
      <div className="absolute -bottom-40 -left-24 size-96 rounded-full bg-primary-foreground/10 sm:size-128" />
      <div className="absolute top-1/3 -left-16 hidden size-48 rounded-full border-12 border-primary-foreground/10 sm:block" />
      <div className="absolute right-[12%] bottom-[18%] hidden size-24 rounded-full border-2 border-dashed border-primary-foreground/30 sm:block" />
    </div>
  );
}

// shortcut: chỉ đăng nhập email + mật khẩu; đăng ký, Google, quên mật khẩu do TV1 làm.
/** Đăng nhập theo LoginPage của openevent: nền cam, logo, thẻ đăng nhập ở giữa. */
export default function LoginPage() {
  const session = useSession();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = safeRedirect(searchParams.get('redirect'));
  if (session && !submitting) {
    return <Navigate to={redirect ?? homePathFor(session)} replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const next = await login(email.trim(), password);
      setSession(next);
      navigate(redirect ?? homePathFor(next), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    // Chữ trắng trên nền cam phải semibold trở lên (DESIGN.md §2).
    <div className="relative isolate flex min-h-svh flex-col overflow-hidden bg-primary px-4 py-5 text-primary-foreground sm:px-8 sm:py-6">
      <Decorations />

      <header className="flex items-center justify-between gap-4">
        <Link to="/" className="flex items-center gap-2 font-bold" aria-label="OpenEvent">
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary-foreground text-primary sm:size-11">
            <CalendarDays className="size-5 sm:size-6" aria-hidden />
          </span>
          <span className="text-lg sm:text-xl">OpenEvent</span>
        </Link>
        <Button
          asChild
          variant="ghost"
          size="sm"
          className="font-semibold text-primary-foreground hover:bg-primary-foreground/15 hover:text-primary-foreground"
        >
          <Link to="/events">
            <ArrowLeft />
            Sự kiện
          </Link>
        </Button>
      </header>

      <main className="mx-auto flex w-full max-w-md flex-1 items-center py-10 sm:py-12">
        <section className="w-full space-y-7 rounded-3xl bg-card px-5 py-7 text-card-foreground shadow-2xl sm:px-8 sm:py-8">
          <div className="space-y-3 text-center">
            <h1 className="text-2xl font-bold tracking-tight md:text-[28px]">Chào mừng bạn</h1>
            <p className="text-sm text-muted-foreground">Đăng nhập bằng tài khoản OpenEvent để mua vé và check-in sự kiện.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="ban@example.com"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Mật khẩu</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
            <Button type="submit" className="h-11 w-full rounded-full font-semibold" disabled={submitting || !email || !password}>
              {submitting && <Spinner />}
              Đăng nhập
            </Button>
          </form>
        </section>
      </main>
    </div>
  );
}
