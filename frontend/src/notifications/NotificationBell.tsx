import { Bell } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { errorMessage } from '@/api/client';
import {
  type AppNotification,
  markNotificationRead,
  myNotifications,
  NOTIFICATION_TARGET,
} from '@/api/notifications';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Spinner } from '@/components/ui/spinner';

import { NotificationItem } from './NotificationItem';
import { decrementUnreadCount, setUnreadCount, useUnreadCount } from './unreadCount';

const LATEST = 5;

/** Chuông trên thanh điều hướng: số chưa đọc và vài thông báo mới nhất. Bấm một thông báo thì đánh dấu đã đọc và mở trang liên quan. */
export function NotificationBell() {
  const unread = useUnreadCount(true);
  const navigate = useNavigate();
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setError(null);
    try {
      const page = await myNotifications(0, LATEST);
      setItems(page.content);
      setUnreadCount(page.unreadCount);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  function open(notification: AppNotification) {
    if (!notification.read) {
      decrementUnreadCount();
      // Không chờ: chuyển trang ngay, đánh dấu chạy nền.
      void markNotificationRead(notification.id).catch(() => undefined);
    }
    navigate(NOTIFICATION_TARGET[notification.type]);
  }

  const label = unread > 0 ? `Thông báo, ${unread} chưa đọc` : 'Thông báo';

  return (
    <DropdownMenu onOpenChange={(isOpen) => isOpen && void load()}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" className="relative" aria-label={label}>
          <Bell />
          {unread > 0 && (
            <span className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[min(22rem,calc(100vw-2rem))] p-0">
        <DropdownMenuLabel className="flex items-center justify-between px-3 py-2">
          <span className="font-semibold">Thông báo</span>
          {unread > 0 && <span className="text-xs font-normal text-muted-foreground">{unread} chưa đọc</span>}
        </DropdownMenuLabel>
        <DropdownMenuSeparator className="m-0" />
        <div className="max-h-96 overflow-y-auto">
          {error ? (
            <p className="px-3 py-6 text-center text-sm text-destructive">{error}</p>
          ) : items === null ? (
            <div className="flex justify-center py-6">
              <Spinner />
            </div>
          ) : items.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">Chưa có thông báo nào.</p>
          ) : (
            items.map((notification) => (
              <DropdownMenuItem
                key={notification.id}
                className="rounded-none px-3 py-3"
                onSelect={() => open(notification)}
              >
                <NotificationItem notification={notification} compact />
              </DropdownMenuItem>
            ))
          )}
        </div>
        <DropdownMenuSeparator className="m-0" />
        <DropdownMenuItem asChild className="justify-center rounded-none py-2.5 text-sm font-medium text-accent-foreground">
          <Link to="/notifications">Xem tất cả thông báo</Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
