import { Building2, CalendarDays, MapPin, Tag, Users } from 'lucide-react';
import { type ReactNode, useState } from 'react';

import type { EventItem } from '@/api/events';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { formatDate, formatDateRange, formatLongDate, formatTime } from '@/lib/datetime';
import { displayStatus, STATUS_TONE_CLASSES } from '@/lib/eventStatus';
import { cn } from '@/lib/utils';
import { EventDescription } from './EventDescription';

type EventDetailViewProps = {
  event: EventItem;
  /** Phần cuối thẻ thông tin: nút mua vé ở trang công khai, nút thao tác ở trang quản lý. */
  aside?: ReactNode;
};

/**
 * Trang chi tiết sự kiện theo EventDetailPage của openevent: ảnh bìa, tiêu đề kèm badge, mục Giới thiệu bên trái,
 * thẻ thông tin dính bên phải từ `lg`.
 */
export function EventDetailView({ event, aside }: EventDetailViewProps) {
  return (
    <div className="space-y-6 lg:space-y-8">
      {event.status === 'CANCELLED' && (
        <div role="status" className="rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          <p className="font-semibold">Sự kiện đã bị hủy</p>
          <p>Sự kiện sẽ không diễn ra. Thông tin dưới đây chỉ để xem lại.</p>
        </div>
      )}
      {event.status === 'COMPLETED' && (
        <div role="status" className="rounded-xl border bg-muted p-4 text-sm text-muted-foreground">
          <p className="font-semibold text-foreground">Sự kiện đã kết thúc</p>
          <p>Cảm ơn bạn đã quan tâm. Thông tin dưới đây chỉ để xem lại.</p>
        </div>
      )}
      <EventCover url={event.bannerUrl} title={event.title} />
      {/* Thẻ thông tin chiếm cả hai hàng, phần cao dư dồn xuống hàng nội dung chứ không nằm dưới tiêu đề. */}
      <div className="grid items-start gap-8 lg:grid-cols-3 lg:grid-rows-[auto_1fr] lg:gap-x-10">
        <div className="lg:col-span-2">
          <EventHeader event={event} />
        </div>
        <div className="lg:col-start-3 lg:row-span-2 lg:row-start-1 lg:self-stretch">
          <EventInfoCard event={event} aside={aside} />
        </div>
        <div className="space-y-8 lg:col-span-2 lg:space-y-10">
          <section className="space-y-4 border-t pt-6 lg:pt-8">
            <h2 className="text-xl font-bold tracking-tight">Giới thiệu</h2>
            <EventDescription description={event.description} />
          </section>
        </div>
      </div>
    </div>
  );
}

const COVER_CLASS = 'aspect-video w-full rounded-xl sm:aspect-2/1 lg:aspect-3/1 lg:rounded-2xl';

export function EventCover({ url, title }: { url: string | null; title: string }) {
  // Ảnh lỗi thì hiện khung mặc định; đổi đường dẫn thì thử lại.
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);

  if (!url || brokenUrl === url) {
    return (
      <div
        role="img"
        aria-label="Chưa có ảnh bìa"
        className={cn(COVER_CLASS, 'flex items-center justify-center bg-linear-to-br from-accent to-surface-alt text-accent-foreground/60')}
      >
        <CalendarDays className="size-14" strokeWidth={1.5} aria-hidden />
      </div>
    );
  }
  return (
    <img
      src={url}
      alt={title}
      onError={() => setBrokenUrl(url)}
      className={cn(COVER_CLASS, 'bg-muted object-cover shadow-sm')}
    />
  );
}

function EventHeader({ event }: { event: EventItem }) {
  const status = displayStatus(event);
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <Badge variant="outline" size="label">
          {event.category.name}
        </Badge>
        <Badge variant="outline" size="label" className={STATUS_TONE_CLASSES[status.tone]}>
          {status.label}
        </Badge>
      </div>
      <h1 className="text-2xl leading-tight font-bold tracking-tight text-balance lg:text-3xl">{event.title}</h1>
      {event.organizationName && (
        <p className="flex flex-wrap items-center gap-x-1.5 text-sm text-muted-foreground">
          <Building2 className="size-4" aria-hidden />
          Tổ chức bởi <span className="font-medium text-foreground">{event.organizationName}</span>
        </p>
      )}
    </div>
  );
}

function InfoRow({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 text-muted-foreground [&_svg]:size-4">{icon}</span>
      <div className="min-w-0 flex-1 space-y-0.5">
        <p className="text-[13px] text-muted-foreground">{label}</p>
        <div className="text-sm font-medium">{children}</div>
      </div>
    </div>
  );
}

/** Ô lịch: số ngày trên "THG 11". */
function DateTile({ value }: { value: string }) {
  const [day, month] = formatDate(value).split('/');
  return (
    <div className="flex w-14 shrink-0 flex-col items-center rounded-lg border bg-background py-1.5">
      <span className="text-[10px] font-semibold tracking-wider text-accent-foreground uppercase">Thg {Number(month)}</span>
      <span className="text-xl leading-tight font-bold">{day}</span>
    </div>
  );
}

function EventInfoCard({ event, aside }: { event: EventItem; aside?: ReactNode }) {
  const multiDay = formatDate(event.startTime) !== formatDate(event.endTime);
  return (
    <Card className="gap-0 rounded-2xl py-0 shadow-sm lg:sticky lg:top-24">
      <CardContent className="space-y-5 p-5">
        <div className="flex items-center gap-3">
          <DateTile value={event.startTime} />
          <div className="min-w-0 text-sm">
            {multiDay ? (
              <p className="font-semibold">{formatDateRange(event.startTime, event.endTime)}</p>
            ) : (
              <>
                <p className="font-semibold">{formatLongDate(event.startTime)}</p>
                <p className="text-muted-foreground">
                  {formatTime(event.startTime)} – {formatTime(event.endTime)}
                </p>
              </>
            )}
          </div>
        </div>

        <div className="space-y-4">
          <InfoRow icon={<MapPin />} label="Địa điểm">
            {event.venue.name}
            <p className="mt-0.5 text-[13px] font-normal text-muted-foreground">
              {event.venue.address}, {event.venue.city}
            </p>
          </InfoRow>
          <InfoRow icon={<Tag />} label="Danh mục">
            {event.category.name}
          </InfoRow>
          {event.venue.capacity ? (
            <InfoRow icon={<Users />} label="Sức chứa địa điểm">
              {event.venue.capacity} người
            </InfoRow>
          ) : null}
        </div>

        {aside && (
          <div className="space-y-4">
            <Separator />
            {aside}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
