import { QRCodeSVG } from 'qrcode.react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { errorMessage } from '@/api/client';
import { myTickets, type Ticket, TICKET_STATUS_BADGE, TICKET_STATUS_LABELS } from '@/api/tickets';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { formatDateRange, formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

/** Vé của tôi: mỗi vé một mã QR để đưa người soát vé quét. */
export default function MyTicketsPage() {
  const [tickets, setTickets] = useState<Ticket[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setError(null);
    myTickets()
      .then((data) => !ignore && setTickets(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  if (error) return <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  if (!tickets) return <LoadingState lines={6} />;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Vé của tôi</h1>
      {tickets.length === 0 ? (
        <EmptyState
          title="Bạn chưa có vé nào"
          description="Vé xuất hiện ở đây sau khi thanh toán thành công."
          action={
            <Button asChild>
              <Link to="/events">Khám phá sự kiện</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tickets.map((ticket) => (
            <Card key={ticket.id} className={cn('py-0', ticket.status !== 'ISSUED' && 'opacity-70')}>
              <CardContent className="flex flex-col items-center gap-3 p-5 text-center">
                <Badge variant={TICKET_STATUS_BADGE[ticket.status]}>{TICKET_STATUS_LABELS[ticket.status]}</Badge>
                <div className="rounded-lg bg-white p-3">
                  <QRCodeSVG value={ticket.code} size={168} aria-label={`Mã QR vé ${ticket.ticketTypeName ?? ''}`} />
                </div>
                <code className="text-xs break-all text-muted-foreground">{ticket.code}</code>
                <div>
                  <p className="font-semibold">{ticket.ticketTypeName ?? 'Vé'}</p>
                  {ticket.eventStartTime && ticket.eventEndTime && (
                    <p className="text-sm text-muted-foreground">
                      {formatDateRange(ticket.eventStartTime, ticket.eventEndTime)}
                    </p>
                  )}
                  {ticket.checkedInAt && (
                    <p className="text-xs text-muted-foreground">Check-in lúc {formatDateTime(ticket.checkedInAt)}</p>
                  )}
                </div>
                {ticket.eventId && (
                  <Link to={`/events/${ticket.eventId}`} className="text-sm font-medium text-accent-foreground hover:underline">
                    Xem sự kiện
                  </Link>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
