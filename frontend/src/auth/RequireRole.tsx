import { Navigate, Outlet, useLocation } from 'react-router';

import { hasRole, type Role, useSession } from './session';

/** Chưa đăng nhập thì về trang đăng nhập rồi quay lại; sai vai trò hoặc có vai trò bị loại trừ thì báo không có quyền. */
export function RequireRole({ role, exclude }: { role: Role; exclude?: Role }) {
  const session = useSession();
  const location = useLocation();

  if (!session) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  if (!hasRole(session, role) || (exclude && hasRole(session, exclude))) {
    return (
      <div role="alert" className="flex min-h-screen flex-col items-center justify-center gap-2 text-center">
        <p className="text-lg font-semibold">Bạn không có quyền truy cập trang này</p>
        <a href="/" className="font-medium text-accent-foreground hover:underline">
          Về trang chủ
        </a>
      </div>
    );
  }
  return <Outlet />;
}
