import { CalendarDays, MapPin, ScanLine } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { errorMessage } from '@/api/client';
import { type EventItem, listCheckInEvents } from '@/api/events';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { formatDateRange } from '@/lib/datetime';

/** Chọn sự kiện để soát vé: sự kiện mình làm HOST, STAFF, hoặc OWNER/MANAGER của tổ chức. */
export default function CheckInEventsPage() {
  const [events, setEvents] = useState<EventItem[]>();
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    setError(undefined);
    listCheckInEvents().then(setEvents).catch((err: unknown) => setError(errorMessage(err)));
  }, [reloadKey]);

  if (error) return <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  if (!events) return <LoadingState lines={4} />;

  return (
    <div className="text-sm">
      <PageHeader
        title="Check-in"
        description="Chọn sự kiện rồi quét mã QR trên vé. Mỗi vé chỉ check-in một lần, trong những ngày sự kiện diễn ra."
      />
      {events.length === 0 ? (
        <EmptyState
          title="Chưa có sự kiện để soát vé"
          description="Sự kiện đã công bố mà bạn làm HOST, STAFF, hoặc OWNER/MANAGER của tổ chức sẽ hiện ở đây."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {events.map((event) => (
            <Card key={event.id} className="py-0">
              <CardContent className="flex h-full flex-col gap-3 p-4">
                <h2 className="line-clamp-2 text-base leading-tight font-semibold">{event.title}</h2>
                <p className="flex items-center gap-2 text-muted-foreground">
                  <CalendarDays className="size-4 shrink-0" aria-hidden />
                  {formatDateRange(event.startTime, event.endTime)}
                </p>
                <p className="flex items-center gap-2 text-muted-foreground">
                  <MapPin className="size-4 shrink-0" aria-hidden />
                  <span className="truncate">{event.venue.name}</span>
                </p>
                <Button asChild className="mt-auto w-full">
                  <Link to={`/organizer/check-in/${event.id}`}>
                    <ScanLine />
                    Soát vé
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
