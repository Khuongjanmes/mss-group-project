import { Building2, CalendarDays, ScanLine } from 'lucide-react';

import { signOut } from '@/api/auth';
import { useSession } from '@/auth/session';
import { DashboardLayout } from '@/components/common/layout';
import type { NavGroup } from '@/components/common/navigation';
import { SidebarUserMenu } from '@/components/common/user';

const organizerNav: NavGroup[] = [
  {
    label: 'Sự kiện',
    items: [
      { title: 'Sự kiện của tôi', to: '/organizer/events', icon: CalendarDays },
      { title: 'Tổ chức', to: '/organizer/organizations', icon: Building2 },
      { title: 'Check-in', to: '/organizer/check-in', icon: ScanLine },
    ],
  },
];

export default function OrganizerLayout() {
  const session = useSession();
  // RequireRole đã đảm bảo có phiên; khi vừa đăng xuất thì guard sẽ chuyển trang ngay sau.
  const user = session
    ? { name: session.user.fullName, email: session.user.email, avatarUrl: session.user.avatarUrl ?? undefined }
    : null;

  return (
    <DashboardLayout
      nav={organizerNav}
      homeTo="/organizer"
      subtitle="Ban tổ chức"
      sidebarFooter={user && <SidebarUserMenu user={user} onSignOut={() => void signOut()} />}
    />
  );
}
