import { CheckCircle2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';

import { resetPassword } from '@/api/auth';
import { errorCode, errorMessage } from '@/api/client';
import { AuthShell } from '@/auth/AuthShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

const MIN_PASSWORD = 8;

/** Đặt mật khẩu mới từ link trong email (`?token=`); xong thì mọi phiên cũ bị đăng xuất, đăng nhập lại bằng mật khẩu mới. */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token')?.trim() ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [invalidLink, setInvalidLink] = useState(!token);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if ([...password].length < MIN_PASSWORD) return setError(`Mật khẩu có ít nhất ${MIN_PASSWORD} ký tự`);
    if (confirm !== password) return setError('Mật khẩu nhập lại không khớp');
    setError(null);
    setSubmitting(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      if (errorCode(err) === 'RESET_TOKEN_INVALID') setInvalidLink(true);
      else setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <AuthShell title="Đã đổi mật khẩu" description="Mật khẩu mới đã có hiệu lực; các phiên đăng nhập cũ đã bị đăng xuất.">
        <div className="flex justify-center">
          <CheckCircle2 className="size-12 text-success" aria-hidden />
        </div>
        <Button asChild className="h-11 w-full rounded-full font-semibold">
          <Link to="/login">Đăng nhập</Link>
        </Button>
      </AuthShell>
    );
  }

  if (invalidLink) {
    return (
      <AuthShell title="Link không còn dùng được" description="Link đặt lại mật khẩu sai, đã dùng rồi hoặc đã quá 30 phút.">
        <Button asChild className="h-11 w-full rounded-full font-semibold">
          <Link to="/forgot-password">Gửi link mới</Link>
        </Button>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Đặt mật khẩu mới" description="Mật khẩu có ít nhất 8 ký tự. Sau khi đổi, bạn cần đăng nhập lại trên mọi thiết bị.">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="password">Mật khẩu mới</Label>
          <Input id="password" type="password" autoComplete="new-password" required value={password}
            onChange={(event) => setPassword(event.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm">Nhập lại mật khẩu mới</Label>
          <Input id="confirm" type="password" autoComplete="new-password" required value={confirm}
            onChange={(event) => setConfirm(event.target.value)} />
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" className="h-11 w-full rounded-full font-semibold" disabled={submitting || !password || !confirm}>
          {submitting && <Spinner />}
          Đổi mật khẩu
        </Button>
      </form>
    </AuthShell>
  );
}
