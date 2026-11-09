import { CalendarDays, ClipboardCheck, CreditCard, MapPin, Tags } from 'lucide-react';

import type { NavGroup } from '@/components/common/navigation';

/** Menu khu quản trị: Admin duyệt sự kiện, không tổ chức. Danh mục, địa điểm, giao dịch, người dùng, dashboard thêm vào đây. */
export const adminNav: NavGroup[] = [
  {
    label: 'Sự kiện',
    items: [
      { title: 'Tất cả sự kiện', to: '/admin/events', icon: CalendarDays, end: true },
      { title: 'Chờ duyệt', to: '/admin/events/pending', icon: ClipboardCheck },
    ],
  },
  {
    label: 'Dữ liệu dùng chung',
    items: [
      { title: 'Danh mục sự kiện', to: '/admin/categories', icon: Tags },
      { title: 'Địa điểm', to: '/admin/venues', icon: MapPin },
    ],
  },
  {
    label: 'Thanh toán',
    items: [{ title: 'Giao dịch', to: '/admin/payments', icon: CreditCard }],
  },
];
