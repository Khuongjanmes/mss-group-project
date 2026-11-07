import { MapPin } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import type { EventItem } from '@/api/events';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, formatTime } from '@/lib/datetime';
import { displayStatus, STATUS_TONE_CLASSES } from '@/lib/eventStatus';
import { cn } from '@/lib/utils';

const headClass = 'h-10 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase';

type EventListTableProps = {
  events: EventItem[];
  /** Đường dẫn trang chi tiết của một sự kiện (khác nhau giữa Organizer và Admin). */
  detailPath: (event: EventItem) => string;
  actions: (event: EventItem) => ReactNode;
  /** Làm mờ các dòng khi đang tải trang hoặc bộ lọc mới. */
  stale?: boolean;
};

/** Bảng sự kiện theo EventListTable của openevent, dùng chung cho Organizer và Admin. */
export function EventListTable({ events, detailPath, actions, stale }: EventListTableProps) {
  return (
    <div className="overflow-x-auto">
      <Table aria-busy={stale} className={cn(stale && 'opacity-60 transition-opacity')}>
        <TableHeader className="border-b border-border/50 bg-surface-alt/50">
          <TableRow className="hover:bg-transparent">
            <TableHead className={cn(headClass, 'w-[380px] px-4')}>Tên sự kiện</TableHead>
            <TableHead className={headClass}>Danh mục</TableHead>
            <TableHead className={headClass}>Thời gian</TableHead>
            <TableHead className={headClass}>Trạng thái</TableHead>
            <TableHead className={cn(headClass, 'px-4 text-right')}>Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {events.map((event) => {
            const status = displayStatus(event);
            return (
              <TableRow key={event.id} className="group border-b border-border/40 transition-colors hover:bg-accent/30">
                <TableCell className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <div className="relative size-11 shrink-0 overflow-hidden rounded-lg border bg-muted">
                      {event.bannerUrl ? (
                        <img src={event.bannerUrl} alt="" loading="lazy" className="size-full object-cover" />
                      ) : (
                        <div className="absolute inset-0 bg-linear-to-br from-accent to-surface-alt" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <Link
                        to={detailPath(event)}
                        title={event.title}
                        className="mb-1 line-clamp-1 text-[14px] font-bold text-foreground transition-colors group-hover:text-primary hover:underline"
                      >
                        {event.title}
                      </Link>
                      <div className="flex items-center text-[12px] font-medium text-muted-foreground">
                        <MapPin className="mr-1.5 size-3.5 shrink-0 text-primary/70" aria-hidden />
                        <span className="line-clamp-1">
                          {event.venue.name} · {event.venue.city}
                        </span>
                      </div>
                      {event.organizationName && (
                        <p className="mt-0.5 line-clamp-1 text-[11px] text-muted-foreground">{event.organizationName}</p>
                      )}
                    </div>
                  </div>
                </TableCell>

                <TableCell className="py-3">
                  <Badge
                    variant="outline"
                    className="rounded-md border-primary/20 bg-accent px-2 py-0.5 text-[11px] font-bold text-accent-foreground shadow-none"
                  >
                    {event.category.name}
                  </Badge>
                </TableCell>

                <TableCell className="py-3">
                  <Schedule event={event} />
                </TableCell>

                <TableCell className="max-w-56 py-3">
                  <Badge
                    variant="outline"
                    className={cn(
                      'flex w-fit items-center rounded-md px-2.5 py-1 text-[11px] font-bold shadow-none',
                      STATUS_TONE_CLASSES[status.tone],
                    )}
                  >
                    {status.tone === 'live' && (
                      <span className="mr-1.5 size-1.5 animate-pulse rounded-full bg-live-foreground" />
                    )}
                    {status.label}
                  </Badge>
                  {event.status === 'REJECTED' && event.rejectReason ? (
                    <p className="mt-1 truncate text-[11px] text-destructive" title={event.rejectReason}>
                      {event.rejectReason}
                    </p>
                  ) : (
                    status.hint && <p className="mt-1 text-[11px] text-muted-foreground">{status.hint}</p>
                  )}
                </TableCell>

                <TableCell className="px-4 py-3 text-right">{actions(event)}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function Schedule({ event }: { event: EventItem }) {
  const startDate = formatDate(event.startTime);
  const endDate = formatDate(event.endTime);
  return (
    <div className="text-[13px] leading-relaxed font-semibold whitespace-nowrap text-foreground">
      <div>{startDate === endDate ? startDate : `${startDate.slice(0, 5)} – ${endDate}`}</div>
      <div>
        {formatTime(event.startTime)} – {formatTime(event.endTime)}
      </div>
    </div>
  );
}
