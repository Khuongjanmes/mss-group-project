import { CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, MapPin, Tag, X, XCircle } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { type ReactNode, useEffect, useState } from 'react';

import { useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet';
import { formatDateRange, formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';
import type { EventTickets } from './myTicketsDisplay';

/** Hỏi lại trong lúc mở để người mua thấy "Đã check-in" ngay khi được quét. */
const POLL_MS = 5000;
const DESCRIPTION = 'Đưa mã cho người soát vé quét khi đến sự kiện. Mỗi vé một mã, chỉ check-in được một lần.';

function useWideScreen() {
  const query = '(min-width: 768px)';
  const [wide, setWide] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const onChange = () => setWide(media.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);
  return wide;
}

type Props = {
  group: EventTickets;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Tải lại vé của tôi (để thấy check-in). */
  onPoll: () => void;
};

/**
 * Vé check-in theo CheckInTicket của openevent: QR bên trái, người giữ vé và thông tin bên phải (từ md, dạng hộp
 * thoại); điện thoại là bottom sheet. Nhiều vé thì chuyển qua lại "Vé 1/3". Mã QR ở đây cố định (không đổi 30 giây).
 */
export function TicketQrDialog({ group, open, onOpenChange, onPoll }: Props) {
  const wide = useWideScreen();
  const title = group.event?.title ?? 'Vé của bạn';
  const waiting = group.tickets.some((t) => t.status === 'ISSUED');

  useEffect(() => {
    if (!open || !waiting) return;
    const timer = setInterval(onPoll, POLL_MS);
    return () => clearInterval(timer);
  }, [open, waiting, onPoll]);

  if (wide) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-h-[92dvh] gap-5 overflow-y-auto rounded-3xl p-6 sm:max-w-3xl" onOpenAutoFocus={(e) => e.preventDefault()}>
          <DialogHeader className="pr-8">
            <DialogTitle className="line-clamp-2 leading-snug">{title}</DialogTitle>
            <DialogDescription>{DESCRIPTION}</DialogDescription>
          </DialogHeader>
          <TicketBody group={group} twoColumns />
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        showCloseButton={false}
        className="max-h-[94dvh] gap-0 overflow-y-auto rounded-t-[28px] border-t-0 px-5 pt-2 pb-[max(1.25rem,env(safe-area-inset-bottom))]"
      >
        <div className="mx-auto mb-3 h-1.5 w-10 shrink-0 rounded-full bg-muted-foreground/25" aria-hidden />
        <div className="mb-4 flex items-start gap-3">
          <div className="min-w-0 flex-1 space-y-1">
            <SheetTitle className="line-clamp-2 text-base leading-snug">{title}</SheetTitle>
            <SheetDescription className="text-[13px]">{DESCRIPTION}</SheetDescription>
          </div>
          <SheetClose asChild>
            <Button variant="secondary" size="icon" className="-mt-0.5 size-9 shrink-0 rounded-full" aria-label="Đóng">
              <X className="size-4" />
            </Button>
          </SheetClose>
        </div>
        <TicketBody group={group} />
      </SheetContent>
    </Sheet>
  );
}

function TicketBody({ group, twoColumns = false }: { group: EventTickets; twoColumns?: boolean }) {
  const session = useSession();
  // Vé chưa dùng lên trước để mở ra là quét được ngay.
  const tickets = [...group.tickets].sort((a, b) => Number(a.status !== 'ISSUED') - Number(b.status !== 'ISSUED'));
  const [index, setIndex] = useState(0);
  const ticket = tickets[Math.min(index, tickets.length - 1)];
  const venue = group.event?.venue;

  return (
    <div className={cn('flex flex-col gap-4', twoColumns && 'grid grid-cols-[minmax(0,320px)_minmax(0,1fr)] items-center gap-6')}>
      <div className="space-y-3 rounded-3xl bg-muted/60 p-4">
        {ticket.status === 'CHECKED_IN' ? (
          <div role="status" className="flex flex-col items-center gap-2 py-8 text-center">
            <CheckCircle2 className="size-14 text-success" aria-hidden />
            <p className="text-lg font-semibold">Đã check-in</p>
            {ticket.checkedInAt && <p className="text-sm text-muted-foreground">Lúc {formatDateTime(ticket.checkedInAt)}</p>}
          </div>
        ) : ticket.status === 'CANCELLED' ? (
          <div role="status" className="flex flex-col items-center gap-2 py-8 text-center">
            <XCircle className="size-14 text-destructive" aria-hidden />
            <p className="text-lg font-semibold">Vé đã bị huỷ</p>
            <p className="text-sm text-muted-foreground">Sự kiện bị huỷ; tiền vé được hoàn theo chính sách.</p>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-3">
            {/* Luôn đen trên trắng, có viền trắng, để camera đọc được cả ở chế độ tối. */}
            <div className="w-full max-w-[min(72vw,288px)] rounded-2xl bg-white p-3 shadow-sm">
              <QRCodeSVG
                value={ticket.code}
                size={288}
                level="M"
                marginSize={2}
                bgColor="#ffffff"
                fgColor="#000000"
                className="h-auto w-full"
                title="Mã QR vé"
              />
            </div>
            <code className="max-w-[min(72vw,288px)] text-center text-[11px] break-all text-muted-foreground">{ticket.code}</code>
          </div>
        )}
        {tickets.length > 1 && (
          <div className="flex items-center justify-between gap-2">
            <Button variant="outline" size="icon" className="size-9 rounded-full" aria-label="Vé trước" disabled={index === 0} onClick={() => setIndex(index - 1)}>
              <ChevronLeft />
            </Button>
            <p className="text-sm font-medium tabular-nums">
              Vé {index + 1}/{tickets.length}
            </p>
            <Button
              variant="outline"
              size="icon"
              className="size-9 rounded-full"
              aria-label="Vé sau"
              disabled={index + 1 >= tickets.length}
              onClick={() => setIndex(index + 1)}
            >
              <ChevronRight />
            </Button>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-4">
        <div className={cn('text-center', twoColumns && 'text-left')}>
          <p className="text-lg leading-tight font-semibold">{session?.user.fullName}</p>
          <p className="mt-0.5 text-sm text-muted-foreground">{session?.user.email}</p>
        </div>
        <dl className="divide-y rounded-2xl border text-[13px]">
          <TicketRow icon={Tag} label="Loại vé">
            {ticket.ticketTypeName ?? 'Vé'}
          </TicketRow>
          <TicketRow icon={CalendarDays} label="Thời gian">
            {formatDateRange(group.startTime, group.endTime)}
          </TicketRow>
          <TicketRow icon={MapPin} label="Địa điểm">
            {venue ? `${venue.name} · ${venue.address}, ${venue.city}` : 'Đang cập nhật'}
          </TicketRow>
          <TicketRow icon={Clock3} label="Phát hành lúc">
            {formatDateTime(ticket.issuedAt)}
          </TicketRow>
        </dl>
      </div>
    </div>
  );
}

function TicketRow({ icon: Icon, label, children }: { icon: typeof MapPin; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 px-3.5 py-2.5">
      <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="min-w-0">
        <dt className="text-xs text-muted-foreground">{label}</dt>
        <dd className="font-medium">{children}</dd>
      </div>
    </div>
  );
}
