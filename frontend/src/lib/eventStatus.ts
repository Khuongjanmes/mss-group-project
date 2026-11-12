import type { EventItem, EventStatus } from '@/api/events';
import type { BadgeVariant } from '@/components/ui/badge';

export type StatusTone = 'live' | 'upcoming' | 'pending' | 'draft' | 'rejected' | 'cancelled' | 'ended';

/** Trạng thái hiển thị trong bảng: gộp trạng thái duyệt với tiến độ thời gian, kèm dòng gợi ý (theo openevent). */
export type DisplayStatus = { label: string; tone: StatusTone; hint?: string };

export const STATUS_TONE_CLASSES: Record<StatusTone, string> = {
  live: 'border-transparent bg-live text-live-foreground',
  upcoming: 'border-primary/30 bg-accent text-accent-foreground',
  pending: 'border-ring bg-card text-accent-foreground',
  draft: 'border-border bg-muted text-muted-foreground',
  rejected: 'border-destructive/30 bg-destructive/10 text-destructive',
  cancelled: 'border-destructive/30 bg-destructive/10 text-destructive',
  ended: 'border-border bg-card text-muted-foreground',
};

export function displayStatus(event: EventItem, now: number = Date.now()): DisplayStatus {
  switch (event.status) {
    case 'DRAFT':
      return { label: 'Nháp', tone: 'draft', hint: 'Chưa gửi duyệt' };
    case 'PENDING_APPROVAL':
      return { label: 'Chờ duyệt', tone: 'pending', hint: 'Chờ Admin xử lý' };
    case 'REJECTED':
      return { label: 'Bị từ chối', tone: 'rejected', hint: 'Sửa rồi gửi duyệt lại' };
    case 'CANCELLED':
      return { label: 'Đã hủy', tone: 'cancelled' };
    case 'COMPLETED':
      return { label: 'Đã kết thúc', tone: 'ended' };
    case 'PUBLISHED':
      // Job COMPLETED chạy mỗi phút, nên sự kiện vừa qua giờ kết thúc vẫn có thể còn PUBLISHED.
      if (now >= new Date(event.endTime).getTime()) return { label: 'Đã kết thúc', tone: 'ended' };
      if (now >= new Date(event.startTime).getTime()) return { label: 'Đang diễn ra', tone: 'live' };
      return { label: 'Sắp diễn ra', tone: 'upcoming', hint: 'Đã công bố' };
  }
}

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: 'Nháp',
  PENDING_APPROVAL: 'Chờ duyệt',
  PUBLISHED: 'Đã công bố',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Đã kết thúc',
};

/** Cùng một trạng thái có cùng màu ở mọi màn hình. */
export const EVENT_STATUS_BADGE: Record<EventStatus, BadgeVariant> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'destructive',
  CANCELLED: 'destructive',
  COMPLETED: 'info',
};
