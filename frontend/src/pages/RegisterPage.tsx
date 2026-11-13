import { type ComponentProps, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';

import { register } from '@/api/auth';
import { errorCode, errorMessage, fieldErrors } from '@/api/client';
import { AuthShell } from '@/auth/AuthShell';
import { GoogleSignInButton } from '@/auth/GoogleSignInButton';
import { homePathFor, safeRedirect, setSession, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

type Field = 'fullName' | 'email' | 'password' | 'confirm';
const MIN_PASSWORD = 8;

/** Lỗi theo từng ô trước khi gửi; server vẫn kiểm tra lại (email ASCII, mật khẩu ≤ 72 byte). */
function validate(form: Record<Field, string>): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {};
  if (!form.fullName.trim()) errors.fullName = 'Nhập họ tên';
  if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) errors.email = 'Email không hợp lệ';
  if ([...form.password].length < MIN_PASSWORD) errors.password = `Mật khẩu có ít nhất ${MIN_PASSWORD} ký tự`;
  if (form.confirm !== form.password) errors.confirm = 'Mật khẩu nhập lại không khớp';
  return errors;
}

/** Đăng ký tài khoản (Attendee #1), cùng khung với trang đăng nhập; đăng ký xong là đăng nhập luôn. */
export default function RegisterPage() {
  const session = useSession();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form, setForm] = useState<Record<Field, string>>({ fullName: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = safeRedirect(searchParams.get('redirect'));
  if (session && !submitting) {
    return <Navigate to={redirect ?? homePathFor(session)} replace />;
  }

  const set = (field: Field, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSubmitting(true);
    try {
      const next = await register(form.fullName.trim(), form.email.trim(), form.password);
      setSession(next);
      navigate(redirect ?? homePathFor(next), { replace: true });
    } catch (err) {
      if (errorCode(err) === 'EMAIL_TAKEN') setErrors({ email: 'Email này đã có tài khoản' });
      else if (Object.keys(fieldErrors(err)).length > 0) setErrors(fieldErrors(err) as Partial<Record<Field, string>>);
      else setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  const field = (id: Field, label: string, props: ComponentProps<typeof Input>) => (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        required
        value={form[id]}
        onChange={(event) => set(id, event.target.value)}
        aria-invalid={Boolean(errors[id]) || undefined}
        aria-describedby={errors[id] ? `${id}-error` : undefined}
        {...props}
      />
      {errors[id] && (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {errors[id]}
        </p>
      )}
    </div>
  );

  return (
    <AuthShell title="Tạo tài khoản" description="Đăng ký tài khoản OpenEvent để mua vé, nhận vé qua email và tổ chức sự kiện.">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {field('fullName', 'Họ tên', { autoComplete: 'name', maxLength: 100, placeholder: 'Nguyễn Văn A' })}
        {field('email', 'Email', { type: 'email', autoComplete: 'email', maxLength: 254, placeholder: 'ban@example.com' })}
        {field('password', 'Mật khẩu', { type: 'password', autoComplete: 'new-password', placeholder: `Ít nhất ${MIN_PASSWORD} ký tự` })}
        {field('confirm', 'Nhập lại mật khẩu', { type: 'password', autoComplete: 'new-password' })}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" className="h-11 w-full rounded-full font-semibold" disabled={submitting}>
          {submitting && <Spinner />}
          Đăng ký
        </Button>
      </form>
      {/* Có phiên là trang tự chuyển về `redirect`, nên chỉ cần lưu phiên. */}
      <GoogleSignInButton onSignedIn={setSession} />
      <p className="text-center text-sm text-muted-foreground">
        Đã có tài khoản?{' '}
        <Link to={{ pathname: '/login', search: searchParams.toString() }} className="font-semibold text-accent-foreground hover:underline">
          Đăng nhập
        </Link>
      </p>
    </AuthShell>
  );
}
