import { MailCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { forgotPassword } from '@/api/auth';
import { errorMessage } from '@/api/client';
import { AuthShell } from '@/auth/AuthShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

/** Quên mật khẩu: nhập email, server gửi link đặt lại (hết hạn sau 30 phút). Không cho biết email có tài khoản không. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const value = email.trim();
    if (!/^\S+@\S+\.\S+$/.test(value)) {
      setError('Email không hợp lệ');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await forgotPassword(value);
      setSentTo(value);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  if (sentTo) {
    return (
      <AuthShell title="Kiểm tra email của bạn" description="Nếu email có tài khoản OpenEvent, chúng tôi vừa gửi link đặt lại mật khẩu.">
        <div className="flex flex-col items-center gap-3 rounded-2xl bg-muted/60 p-5 text-center text-sm">
          <MailCheck className="size-10 text-success" aria-hidden />
          <p>
            Đã gửi tới <span className="font-semibold">{sentTo}</span>.
          </p>
          <p className="text-muted-foreground">Link hết hạn sau 30 phút và chỉ dùng một lần. Không thấy email thì xem thư mục Spam.</p>
        </div>
        <Button asChild className="h-11 w-full rounded-full font-semibold">
          <Link to="/login">Quay lại đăng nhập</Link>
        </Button>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Quên mật khẩu" description="Nhập email tài khoản OpenEvent, chúng tôi sẽ gửi link để bạn đặt mật khẩu mới.">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="ban@example.com"
            maxLength={254}
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-invalid={Boolean(error) || undefined}
          />
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" className="h-11 w-full rounded-full font-semibold" disabled={submitting || !email.trim()}>
          {submitting && <Spinner />}
          Gửi link đặt lại
        </Button>
      </form>
      <p className="text-center text-sm text-muted-foreground">
        Nhớ ra rồi?{' '}
        <Link to="/login" className="font-semibold text-accent-foreground hover:underline">
          Đăng nhập
        </Link>
      </p>
    </AuthShell>
  );
}
