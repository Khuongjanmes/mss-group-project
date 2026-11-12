import { ArrowRight, CalendarDays, Clock, ImageIcon, MapPin, QrCode, Tag } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { formatDateRange, formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';
import {
  countdownLabel,
  countdownVariant,
  daysUntilStart,
  type EventTickets,
  PHASE_BADGE,
  PHASE_LABELS,
  phaseOf,
  ticketCountText,
  ticketTypesSummary,
} from './myTicketsDisplay';
import { TicketQrDialog } from './TicketQrDialog';

/** Thẻ ngang một sự kiện kèm vé của tôi, theo RegistrationCard của openevent (bỏ nút tim và đánh giá). */
export function TicketEventCard({ group, onPoll }: { group: EventTickets; onPoll: () => void }) {
  const { event, tickets } = group;
  const phase = phaseOf(group);
  const phaseVariant = PHASE_BADGE[phase];
  const days = daysUntilStart(group);
  const [qrOpen, setQrOpen] = useState(false);
  const issuedAt = tickets.map((t) => t.issuedAt).sort()[0];
  const hasQr = tickets.some((t) => t.status !== 'CANCELLED');

  return (
    <div className="group relative flex flex-col gap-4 rounded-xl border bg-card p-4 shadow-sm transition focus-within:ring-[3px] focus-within:ring-ring/50 hover:-translate-y-0.5 hover:shadow-md motion-reduce:hover:translate-y-0 md:flex-row md:gap-5">
      <div className="relative aspect-2/1 w-full shrink-0 overflow-hidden rounded-lg bg-muted md:aspect-auto md:w-72">
        {event?.bannerUrl ? (
          <img src={event.bannerUrl} alt={event.title} className={cn('size-full object-cover', phase === 'cancelled' && 'grayscale')} />
        ) : (
          <div className="flex size-full items-center justify-center">
            <ImageIcon className="size-8 text-muted-foreground" aria-hidden />
          </div>
        )}
        {/* Điện thoại: đếm ngược nằm trên ảnh để tên được cả chiều ngang. */}
        {days !== null ? (
          <Badge variant={countdownVariant(days)} size="label" className="absolute top-2 left-2 md:hidden">
            <Clock aria-hidden />
            {countdownLabel(days)}
          </Badge>
        ) : null}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex items-start justify-between gap-3">
          <h2 className="line-clamp-2 text-base font-semibold group-hover:text-accent-foreground">
            <Link to={`/events/${group.eventId}`} className="after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none">
              {event?.title ?? 'Sự kiện'}
            </Link>
          </h2>
          {days !== null ? (
            <Badge variant={countdownVariant(days)} size="label" className="mt-0.5 hidden shrink-0 md:inline-flex">
              <Clock aria-hidden />
              {countdownLabel(days)}
            </Badge>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {event ? (
            <Badge variant="outline" size="label">
              {event.category.name}
            </Badge>
          ) : null}
          {phaseVariant ? (
            <Badge variant={phaseVariant} size="label">
              {PHASE_LABELS[phase]}
            </Badge>
          ) : null}
          <Badge variant={tickets.some((t) => t.status === 'CHECKED_IN') ? 'success' : 'neutral'} size="label">
            {ticketCountText(tickets)}
          </Badge>
        </div>

        <div className="space-y-1 text-[13px] text-muted-foreground">
          <p className="flex items-start gap-1.5">
            <CalendarDays className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            {formatDateRange(group.startTime, group.endTime)}
          </p>
          <p className="flex items-start gap-1.5">
            <MapPin className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            {event ? `${event.venue.name} · ${event.venue.address}, ${event.venue.city}` : 'Đang cập nhật'}
          </p>
          <p className="flex items-start gap-1.5 font-medium text-foreground">
            <Tag className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden />
            {ticketTypesSummary(tickets)}
          </p>
        </div>

        {hasQr ? (
          // Trên link phủ cả thẻ để bấm được.
          <Button variant="outline" className="relative z-10 h-11 w-full rounded-full md:h-9 md:w-auto md:self-start" onClick={() => setQrOpen(true)}>
            <QrCode />
            Mã QR vé
          </Button>
        ) : null}

        <div className="mt-auto flex items-center justify-between gap-3 border-t pt-2.5">
          <p className="truncate text-xs text-muted-foreground">Phát hành lúc {formatDateTime(issuedAt)}</p>
          <span className="flex shrink-0 items-center gap-1 text-sm font-medium text-accent-foreground">
            <span className="hidden md:inline">Xem chi tiết</span>
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden />
          </span>
        </div>
      </div>
      <TicketQrDialog group={group} open={qrOpen} onOpenChange={setQrOpen} onPoll={onPoll} />
    </div>
  );
}
