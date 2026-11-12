import { ArrowRight, CalendarDays, ChevronLeft, ChevronRight, Clock, ImageIcon, MapPin, ReceiptText } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { type EventItem, getEvent } from '@/api/events';
import {
  cancelOrder,
  isPayable,
  myOrders,
  type Order,
  ORDER_STATUS_BADGE,
  ORDER_STATUS_LABELS,
  payOrder,
} from '@/api/orders';
import { formatMoney } from '@/api/payments';
import { listTicketTypes } from '@/api/tickets';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { formatDateRange, formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

const PAGE_SIZE = 10;

/** Tên sự kiện và tên loại vé: Order chỉ lưu id, nên hỏi Event và Ticket theo từng sự kiện trong trang. */
interface EventInfo {
  event: EventItem | null;
  ticketNames: Record<string, string>;
}

/** Lịch sử đơn (Attendee #8): thanh toán lại hoặc hủy đơn đang chờ thanh toán. */
export default function MyOrdersPage() {
  const [page, setPage] = useState(0);
  const [orders, setOrders] = useState<PageResponse<Order> | null>(null);
  const [events, setEvents] = useState<Record<string, EventInfo>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [paying, setPaying] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<Order | null>(null);
  const now = useNow(orders?.content.some((order) => order.status === 'PENDING') ?? false);

  useEffect(() => {
    let ignore = false;
    setError(null);
    myOrders(page, PAGE_SIZE)
      .then((data) => {
        if (ignore) return;
        setOrders(data);
        void loadEvents(data.content).then((info) => !ignore && setEvents((current) => ({ ...current, ...info })));
      })
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [page, reloadKey]);

  async function pay(order: Order) {
    setPaying(order.id);
    try {
      await payOrder(order.id);
    } catch (err) {
      toast.error(errorMessage(err));
      setPaying(null);
    }
  }

  function replace(updated: Order) {
    setOrders((current) =>
      current && { ...current, content: current.content.map((order) => (order.id === updated.id ? updated : order)) },
    );
  }

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-6.5rem)] flex-col gap-4 lg:min-h-[calc(100dvh-8rem)]">
      <PageHeader title="Đơn của tôi" description="Đơn đặt vé của bạn; đơn chờ thanh toán giữ vé trong 10 phút." />

      <section
        aria-label="Danh sách đơn"
        className={cn('flex flex-1 flex-col', (error || orders?.totalElements === 0) && 'justify-center')}
      >
        {error ? (
          <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />
        ) : !orders ? (
          <LoadingState lines={4} />
        ) : orders.totalElements === 0 ? (
          <EmptyState
            title="Bạn chưa có đơn nào"
            description="Chọn vé ở trang sự kiện để đặt vé."
            action={
              <Button asChild variant="outline">
                <Link to="/events">Xem danh sách sự kiện</Link>
              </Button>
            }
          />
        ) : (
          <ul className="space-y-3">
            {orders.content.map((order) => {
              const info = events[order.eventId];
              const event = info?.event;
              const payable = isPayable(order, now);
              return (
                <li
                  key={order.id}
                  className="group relative flex flex-col gap-4 rounded-xl border bg-card p-4 shadow-sm transition focus-within:ring-[3px] focus-within:ring-ring/50 hover:-translate-y-0.5 hover:shadow-md motion-reduce:hover:translate-y-0 md:flex-row md:gap-5"
                >
                  <div className="relative aspect-2/1 w-full shrink-0 overflow-hidden rounded-lg bg-muted md:aspect-auto md:w-72">
                    {event?.bannerUrl ? (
                      <img
                        src={event.bannerUrl}
                        alt={event.title}
                        className={cn('size-full object-cover', (order.status === 'CANCELLED' || order.status === 'EXPIRED') && 'grayscale')}
                      />
                    ) : (
                      <div className="flex size-full items-center justify-center">
                        <ImageIcon className="size-8 text-muted-foreground" aria-hidden />
                      </div>
                    )}
                  </div>

                  <div className="flex min-w-0 flex-1 flex-col gap-2">
                    <div className="flex items-start justify-between gap-3">
                      <h2 className="line-clamp-2 text-base font-semibold group-hover:text-accent-foreground">
                        <Link
                          to={`/events/${order.eventId}`}
                          className="after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none"
                        >
                          {event?.title ?? 'Sự kiện'}
                        </Link>
                      </h2>
                      <p className="shrink-0 text-lg font-bold tabular-nums">{formatMoney(order.totalAmount)}</p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      {event ? (
                        <Badge variant="outline" size="label">
                          {event.category.name}
                        </Badge>
                      ) : null}
                      <Badge variant={ORDER_STATUS_BADGE[order.status]} size="label">
                        {ORDER_STATUS_LABELS[order.status]}
                      </Badge>
                      {order.status === 'PENDING' && (
                        <Badge variant={payable ? 'warning' : 'neutral'} size="label">
                          <Clock aria-hidden />
                          {payable ? `Giữ vé còn ${remaining(order.expiresAt, now)}` : 'Hết thời gian giữ vé'}
                        </Badge>
                      )}
                    </div>

                    <div className="space-y-1 text-[13px] text-muted-foreground">
                      {event && (
                        <>
                          <p className="flex items-start gap-1.5">
                            <CalendarDays className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                            {formatDateRange(event.startTime, event.endTime)}
                          </p>
                          <p className="flex items-start gap-1.5">
                            <MapPin className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                            {event.venue.name} · {event.venue.address}, {event.venue.city}
                          </p>
                        </>
                      )}
                      <p className="flex items-start gap-1.5 font-medium text-foreground">
                        <ReceiptText className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                        {order.items
                          .map((item) => `${info?.ticketNames[item.ticketTypeId] ?? 'Vé'} ×${item.quantity} · ${formatMoney(item.unitPrice)}`)
                          .join(' · ')}
                      </p>
                    </div>

                    {(payable || order.status === 'PAID') && (
                      // Trên link phủ cả thẻ để bấm được.
                      <div className="relative z-10 flex flex-wrap gap-2 pt-1">
                        {payable ? (
                          <>
                            <Button
                              className="h-11 flex-1 rounded-full md:h-9 md:flex-none"
                              disabled={paying !== null}
                              onClick={() => void pay(order)}
                            >
                              {paying === order.id && <Spinner />}
                              Thanh toán
                            </Button>
                            <Button
                              variant="outline"
                              className="h-11 flex-1 rounded-full md:h-9 md:flex-none"
                              disabled={paying !== null}
                              onClick={() => setCancelling(order)}
                            >
                              Hủy đơn
                            </Button>
                          </>
                        ) : (
                          <Button asChild variant="outline" className="h-11 w-full rounded-full md:h-9 md:w-auto">
                            <Link to="/tickets">Xem vé</Link>
                          </Button>
                        )}
                      </div>
                    )}

                    <div className="mt-auto flex items-center justify-between gap-3 border-t pt-2.5">
                      <p className="truncate text-xs text-muted-foreground">
                        Mã đơn <code>{order.id.slice(0, 8)}</code> · Đặt lúc {formatDateTime(order.createdAt)}
                      </p>
                      <span className="flex shrink-0 items-center gap-1 text-sm font-medium text-accent-foreground">
                        <span className="hidden md:inline">Xem sự kiện</span>
                        <ArrowRight
                          className="size-4 transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
                          aria-hidden
                        />
                      </span>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {orders && orders.totalPages > 1 ? (
        <nav className="flex items-center justify-between gap-4" aria-label="Phân trang">
          <p className="text-sm text-muted-foreground">
            Trang {orders.page + 1}/{orders.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={orders.page === 0} onClick={() => setPage(orders.page - 1)}>
              <ChevronLeft />
              Trước
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={orders.page + 1 >= orders.totalPages}
              onClick={() => setPage(orders.page + 1)}
            >
              Sau
              <ChevronRight />
            </Button>
          </div>
        </nav>
      ) : null}
      {cancelling && (
        <CancelOrderDialog order={cancelling} onCancelled={replace} onClose={() => setCancelling(null)} />
      )}
    </div>
  );
}

/** Hỏi lại trước khi hủy; giữ hộp thoại mở tới khi có kết quả và hiện lỗi của server (ví dụ đơn vừa hết hạn). */
function CancelOrderDialog({
  order,
  onCancelled,
  onClose,
}: {
  order: Order;
  onCancelled: (order: Order) => void;
  onClose: () => void;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <AlertDialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Hủy đơn {formatMoney(order.totalAmount)}?</AlertDialogTitle>
          <AlertDialogDescription>
            Vé đang giữ sẽ được trả lại để người khác mua. Muốn mua lại, bạn cần đặt đơn mới.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <p role="alert" className="text-sm font-medium text-destructive">
            {error}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Quay lại</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={pending}
            onClick={(event) => {
              event.preventDefault();
              setPending(true);
              setError(null);
              cancelOrder(order.id)
                .then((updated) => {
                  onCancelled(updated);
                  toast.success('Đã hủy đơn');
                  onClose();
                })
                .catch((err: unknown) => setError(errorMessage(err)))
                .finally(() => setPending(false));
            }}
          >
            {pending && <Spinner />}
            Hủy đơn
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

async function loadEvents(orders: Order[]): Promise<Record<string, EventInfo>> {
  const eventIds = [...new Set(orders.map((order) => order.eventId))];
  const entries = await Promise.all(
    eventIds.map(async (eventId): Promise<[string, EventInfo]> => {
      // Sự kiện đã hủy hoặc ẩn có thể trả 404: vẫn hiện đơn, chỉ thiếu tên.
      const [event, types] = await Promise.allSettled([getEvent(eventId), listTicketTypes(eventId)]);
      return [
        eventId,
        {
          event: event.status === 'fulfilled' ? event.value : null,
          ticketNames:
            types.status === 'fulfilled' ? Object.fromEntries(types.value.map((type) => [type.id, type.name])) : {},
        },
      ];
    }),
  );
  return Object.fromEntries(entries);
}

/** Đồng hồ cập nhật mỗi giây khi trang có đơn đang giữ vé. */
function useNow(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  return now;
}

function remaining(expiresAt: string, now: number): string {
  const seconds = Math.max(0, Math.floor((Date.parse(expiresAt) - now) / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
