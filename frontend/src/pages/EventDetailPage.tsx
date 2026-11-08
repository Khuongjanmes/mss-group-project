import { ArrowLeft } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { type EventItem, getEvent } from '@/api/events';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { EventDetailView } from '@/events/EventDetailView';
import { RelatedEvents } from '@/events/RelatedEvents';

/** Trang công khai của một sự kiện. Phần loại vé và đặt vé (Ticket, Order) sẽ gắn vào `aside` của thẻ thông tin. */
export default function EventDetailPage() {
  const { id = '' } = useParams();
  const [event, setEvent] = useState<EventItem | null>(null);
  const [error, setError] = useState<{ notFound: boolean; message: string } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setError(null);
    getEvent(id)
      .then((data) => !ignore && setEvent(data))
      .catch((err: unknown) => {
        if (ignore) return;
        const status = (err as { response?: { status?: number } }).response?.status;
        // 404 (chưa công bố hoặc không có) và 400 (id sai định dạng) đều là "không có sự kiện này".
        setError({ notFound: status === 404 || status === 400, message: errorMessage(err) });
      });
    return () => {
      ignore = true;
    };
  }, [id, reloadKey]);

  if (error?.notFound) {
    return (
      <EmptyState
        title="Không tìm thấy sự kiện"
        description="Sự kiện không tồn tại hoặc chưa được công bố."
        action={
          <Button asChild variant="outline">
            <Link to="/">Về trang chủ</Link>
          </Button>
        }
      />
    );
  }
  if (error) return <ErrorState description={error.message} onRetry={() => setReloadKey((k) => k + 1)} />;
  if (!event) return <LoadingState lines={10} />;
  return (
    <div className="space-y-4">
      <Button asChild variant="ghost" size="sm" className="-ml-2 text-muted-foreground">
        <Link to="/events">
          <ArrowLeft className="size-4" />
          Tất cả sự kiện
        </Link>
      </Button>
      <EventDetailView event={event} />
      <RelatedEvents event={event} />
    </div>
  );
}
