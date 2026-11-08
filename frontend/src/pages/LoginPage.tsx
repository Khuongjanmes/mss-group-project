import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';

import { login } from '@/api/auth';
import { errorMessage } from '@/api/client';
import { homePathFor, safeRedirect, setSession, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

// shortcut: chỉ đăng nhập email + mật khẩu; đăng ký, Google, quên mật khẩu do TV1 làm.
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
    <section className="mx-auto max-w-sm py-8">
      <div className="rounded-xl border bg-card p-6 shadow-sm">
        <h1 className="mb-1 text-2xl font-bold tracking-tight">Đăng nhập</h1>
        <p className="mb-6 text-sm text-muted-foreground">Dùng tài khoản OpenEvent của bạn.</p>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
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
          <Button type="submit" className="w-full" disabled={submitting || !email || !password}>
            {submitting && <Spinner />}
            Đăng nhập
          </Button>
        </form>
      </div>
    </section>
  );
}
