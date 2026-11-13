import { CalendarX2, KeyRound, Ticket } from 'lucide-react';

import type { AppNotification, NotificationType } from '@/api/notifications';
import { formatRelative } from '@/lib/datetime';
import { cn } from '@/lib/utils';

const ICON: Record<NotificationType, typeof Ticket> = {
  TICKET_ISSUED: Ticket,
  EVENT_CANCELLED: CalendarX2,
  PASSWORD_RESET: KeyRound,
};

/** Một dòng thông báo, dùng chung cho chuông và trang Thông báo. Chưa đọc thì chữ đậm và có chấm cam. */
export function NotificationItem({ notification, compact = false }: { notification: AppNotification; compact?: boolean }) {
  const Icon = ICON[notification.type];
  const cancelled = notification.type === 'EVENT_CANCELLED';
  return (
    <div className="flex w-full items-start gap-3 text-left">
      <span
        className={cn(
          'mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full',
          cancelled ? 'bg-destructive/10 text-destructive' : 'bg-accent text-accent-foreground',
        )}
        aria-hidden
      >
        <Icon className="size-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className={cn('block text-sm', notification.read ? 'text-foreground' : 'font-semibold text-foreground')}>
          {notification.title}
        </span>
        <span className={cn('block text-xs text-muted-foreground', compact && 'line-clamp-2')}>
          {notification.content}
        </span>
        <span className="mt-1 block text-xs text-muted-foreground">{formatRelative(notification.sentAt)}</span>
      </span>
      {!notification.read && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" aria-label="Chưa đọc" />}
    </div>
  );
}
