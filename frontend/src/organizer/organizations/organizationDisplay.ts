import type { OrganizationRole } from '@/api/organizations';
import type { BadgeVariant } from '@/components/ui/badge';

export const ROLE_BADGE: Record<OrganizationRole, BadgeVariant> = {
  OWNER: 'warning',
  MANAGER: 'soft',
  MEMBER: 'neutral',
};

export const ROLE_DESCRIPTIONS: Record<OrganizationRole, string> = {
  OWNER: 'Toàn quyền: sửa tổ chức, mời và phân quyền thành viên, quản lý mọi sự kiện.',
  MANAGER: 'Tạo và quản lý mọi sự kiện của tổ chức, mời Thành viên.',
  MEMBER: 'Chỉ tạo sự kiện khi được cấp quyền; quản lý sự kiện mình phụ trách.',
};
