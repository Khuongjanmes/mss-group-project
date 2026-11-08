import { signOut } from '@/api/auth';
import { useSession } from '@/auth/session';
import { DashboardLayout } from '@/components/common/layout';
import { SidebarUserMenu } from '@/components/common/user';
import { adminNav } from './adminNav';

export default function AdminLayout() {
  const session = useSession();
  // RequireRole đã đảm bảo có phiên; khi vừa đăng xuất thì guard sẽ chuyển trang ngay sau.
  const user = session
    ? { name: session.user.fullName, email: session.user.email, avatarUrl: session.user.avatarUrl ?? undefined }
    : null;

  return (
    <DashboardLayout
      nav={adminNav}
      homeTo="/admin"
      subtitle="Quản trị"
      sidebarFooter={user && <SidebarUserMenu user={user} onSignOut={() => void signOut()} />}
    />
  );
}
