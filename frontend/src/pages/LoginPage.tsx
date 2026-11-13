import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';

import { login } from '@/api/auth';
import { errorMessage } from '@/api/client';
import { AuthShell } from '@/auth/AuthShell';
import { homePathFor, safeRedirect, setSession, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

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
    <AuthShell title="Chào mừng bạn" description="Đăng nhập bằng tài khoản OpenEvent để mua vé và check-in sự kiện.">
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
      <p className="text-center text-sm text-muted-foreground">
        Chưa có tài khoản?{' '}
        <Link to={{ pathname: '/register', search: searchParams.toString() }} className="font-semibold text-accent-foreground hover:underline">
          Đăng ký
        </Link>
      </p>
    </AuthShell>
  );
}
