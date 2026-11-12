import { ArrowRight, CalendarDays, ImageIcon, MapPin } from 'lucide-react';
import { Link } from 'react-router';

import type { EventItem } from '@/api/events';
import { Badge } from '@/components/ui/badge';
import { formatDate, formatDateTime, formatTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

/**
 * Thẻ ngang của trang sự kiện theo EventCard của openevent. Bỏ nút tim, số chỗ còn, số người tham gia và nút đăng ký
 * vì API ở đây không có các số liệu đó.
 */
export function EventRowCard({ event, now = new Date() }: { event: EventItem; now?: Date }) {
  const start = new Date(event.startTime);
  const end = new Date(event.endTime);
  const isEnded = event.status === 'COMPLETED' || end <= now;
  const isOngoing = !isEnded && start <= now;

  const sameDay = formatDate(event.startTime) === formatDate(event.endTime);
  const timeSummary = sameDay
    ? `${formatDateTime(event.startTime)} – ${formatTime(event.endTime)}`
    : `${formatDateTime(event.startTime)} – ${formatDateTime(event.endTime)}`;

  return (
    <div className="group relative flex flex-col gap-4 rounded-xl border bg-card p-4 shadow-xs transition focus-within:ring-2 focus-within:ring-ring/50 hover:-translate-y-0.5 hover:shadow-md motion-reduce:hover:translate-y-0 md:flex-row md:gap-5">
      <div className="relative aspect-2/1 w-full shrink-0 overflow-hidden rounded-lg bg-muted md:aspect-auto md:w-72">
        {event.bannerUrl ? (
          <img
            src={event.bannerUrl}
            alt={event.title}
            className={cn('size-full object-cover transition duration-300 group-hover:scale-105', isEnded && 'opacity-80')}
          />
        ) : (
          <div className="flex size-full items-center justify-center">
            <ImageIcon className="size-8 text-muted-foreground" aria-hidden />
          </div>
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <h2 className="line-clamp-2 text-base font-semibold transition-colors group-hover:text-primary">
          <Link to={`/events/${event.id}`} className="after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none">
            {event.title}
          </Link>
        </h2>

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant="outline" size="label">
            {event.category.name}
          </Badge>
          {isEnded ? (
            <Badge variant="info" size="label">
              Đã kết thúc
            </Badge>
          ) : isOngoing ? (
            <Badge variant="live" size="label">
              Đang diễn ra
            </Badge>
          ) : null}
        </div>

        <div className="space-y-1 text-sm text-muted-foreground">
          <p className="flex items-start gap-1.5">
            <CalendarDays className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            <span>{timeSummary}</span>
          </p>
          <p className="flex items-start gap-1.5">
            <MapPin className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            <span>
              {event.venue.name} · {event.venue.address}, {event.venue.city}
            </span>
          </p>
        </div>

        <div className="mt-auto flex items-center justify-between gap-3 border-t pt-2.5">
          <p className="truncate text-xs text-muted-foreground">{event.organizationName ?? 'Sự kiện cá nhân'}</p>
          <span className="flex shrink-0 items-center gap-1 text-sm font-medium text-accent-foreground">
            <span className="hidden md:inline">Xem chi tiết</span>
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden />
          </span>
        </div>
      </div>
    </div>
  );
}
