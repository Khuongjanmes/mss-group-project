import { Building2, CalendarDays, MapPin } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import type { EventItem } from '@/api/events';
import { Badge } from '@/components/ui/badge';
import { formatDate, formatLongDate, formatTime } from '@/lib/datetime';
import { displayStatus } from '@/lib/eventStatus';
import { cn } from '@/lib/utils';

/** Cùng ngày: `Thứ Bảy, 07/11/2026 · 10:00`; khác ngày: `07/11/2026 – 08/11/2026`. */
function cardTime(event: EventItem): string {
  const start = formatDate(event.startTime);
  const end = formatDate(event.endTime);
  return start === end ? `${formatLongDate(event.startTime)} · ${formatTime(event.startTime)}` : `${start} – ${end}`;
}

function CardCover({ url }: { url: string | null }) {
  // Ảnh lỗi thì hiện khung mặc định như ảnh bìa ở trang chi tiết.
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);

  if (!url || brokenUrl === url) {
    return (
      <div className="flex size-full items-center justify-center bg-linear-to-br from-accent to-surface-alt text-accent-foreground/60">
        <CalendarDays className="size-10" strokeWidth={1.5} aria-hidden />
      </div>
    );
  }
  return (
    <img
      src={url}
      alt=""
      loading="lazy"
      onError={() => setBrokenUrl(url)}
      className="size-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
    />
  );
}

/** Thẻ sự kiện theo EventCard của openevent SBA301: ảnh bìa có ô ngày, danh mục, tên, thời gian, địa điểm, đơn vị tổ chức. */
export function EventCard({ event }: { event: EventItem }) {
  const status = displayStatus(event);
  const [day, month] = formatDate(event.startTime).split('/');

  return (
    <article className="group relative flex h-full flex-col overflow-hidden rounded-xl border bg-card shadow-sm transition hover:-translate-y-0.5 hover:shadow-md focus-within:ring-2 focus-within:ring-ring">
      <div className="relative aspect-video overflow-hidden bg-muted">
        <CardCover url={event.bannerUrl} />
        <div
          className="absolute top-3 left-3 flex w-12 flex-col items-center rounded-lg bg-card/95 py-1 shadow-sm"
          aria-hidden
        >
          <span className="text-[10px] font-semibold tracking-wider text-accent-foreground uppercase">
            Thg {Number(month)}
          </span>
          <span className="text-lg leading-tight font-bold">{day}</span>
        </div>
        {(status.tone === 'live' || status.tone === 'ended') && (
          <Badge variant={status.tone === 'live' ? 'live' : 'neutral'} size="label" className="absolute top-3 right-3">
            {status.label}
          </Badge>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-4">
        <p className="text-xs font-semibold text-accent-foreground">{event.category.name}</p>
        <h3 className="line-clamp-2 leading-snug font-semibold">
          {/* Liên kết phủ cả thẻ để bấm chỗ nào cũng mở chi tiết. */}
          <Link
            to={`/events/${event.id}`}
            className="outline-none after:absolute after:inset-0 group-hover:text-primary"
          >
            {event.title}
          </Link>
        </h3>
        <ul className="mt-auto space-y-1.5 pt-1 text-[13px] text-muted-foreground [&_svg]:size-4 [&_svg]:shrink-0">
          <li className="flex items-center gap-2">
            <CalendarDays aria-hidden />
            <span className="truncate">{cardTime(event)}</span>
          </li>
          <li className="flex items-center gap-2">
            <MapPin aria-hidden />
            <span className="truncate">
              {event.venue.name} · {event.venue.city}
            </span>
          </li>
          {event.organizationName && (
            <li className="flex items-center gap-2">
              <Building2 aria-hidden />
              <span className="truncate">{event.organizationName}</span>
            </li>
          )}
        </ul>
      </div>
    </article>
  );
}

const GRID_CLASS = 'grid gap-4 sm:grid-cols-2 md:gap-6 lg:grid-cols-3';

export function EventCardGrid({ events, stale = false }: { events: EventItem[]; stale?: boolean }) {
  return (
    <ul className={cn(GRID_CLASS, 'transition-opacity', stale && 'opacity-60')} aria-busy={stale}>
      {events.map((event) => (
        <li key={event.id}>
          <EventCard event={event} />
        </li>
      ))}
    </ul>
  );
}
