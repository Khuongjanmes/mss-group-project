import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { errorMessage } from '@/api/client';
import {
  type AppNotification,
  markNotificationRead,
  myNotifications,
  NOTIFICATION_TARGET,
  type NotificationPage,
} from '@/api/notifications';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { ListPagination } from '@/components/common/navigation';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { NotificationItem } from '@/notifications/NotificationItem';
import { decrementUnreadCount, setUnreadCount } from '@/notifications/unreadCount';

const PAGE_SIZE = 20;

/** Thông báo của tôi (Attendee #10): vé đã phát, sự kiện bị hủy. Bấm vào thì đánh dấu đã đọc và mở trang liên quan. */
export default function NotificationsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setError(null);
    myNotifications(page, PAGE_SIZE)
      .then((result) => {
        if (ignore) return;
        setData(result);
        setUnreadCount(result.unreadCount);
      })
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [page, reloadKey]);

  function open(notification: AppNotification) {
    if (!notification.read) {
      decrementUnreadCount();
      void markNotificationRead(notification.id).catch(() => undefined);
    }
    navigate(NOTIFICATION_TARGET[notification.type]);
  }

  if (error) return <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  if (!data) return <LoadingState lines={6} />;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-baseline justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight">Thông báo</h1>
        {data.unreadCount > 0 && <span className="text-sm text-muted-foreground">{data.unreadCount} chưa đọc</span>}
      </div>
      {data.totalElements === 0 ? (
        <EmptyState
          title="Chưa có thông báo nào"
          description="Bạn sẽ nhận thông báo khi vé được phát hoặc sự kiện bạn mua vé bị hủy."
          action={
            <Button asChild>
              <Link to="/events">Khám phá sự kiện</Link>
            </Button>
          }
        />
      ) : (
        <Card className="gap-0 overflow-hidden py-0">
          <ul className="divide-y">
            {data.content.map((notification) => (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => open(notification)}
                  className="w-full px-4 py-4 transition-colors hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:outline-none"
                >
                  <NotificationItem notification={notification} />
                </button>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && <ListPagination meta={data} onPageChange={setPage} unit="thông báo" />}
        </Card>
      )}
    </div>
  );
}
