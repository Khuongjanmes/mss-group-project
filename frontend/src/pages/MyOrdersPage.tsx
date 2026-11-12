import { Clock } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { getEvent } from '@/api/events';
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
import { ListPagination } from '@/components/common/navigation';
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
import { Card } from '@/components/ui/card';
import { Spinner } from '@/components/ui/spinner';
import { formatDateTime } from '@/lib/datetime';

const PAGE_SIZE = 10;

/** Tên sự kiện và tên loại vé: Order chỉ lưu id, nên hỏi Event và Ticket theo từng sự kiện trong trang. */
interface EventInfo {
  title: string | null;
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

  if (error) return <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  if (!orders) return <LoadingState lines={6} />;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Đơn của tôi</h1>
      {orders.totalElements === 0 ? (
        <EmptyState
          title="Bạn chưa có đơn nào"
          description="Chọn vé ở trang sự kiện để đặt vé."
          action={
            <Button asChild>
              <Link to="/events">Khám phá sự kiện</Link>
            </Button>
          }
        />
      ) : (
        <Card className="gap-0 py-0">
          <ul className="divide-y">
            {orders.content.map((order) => {
              const info = events[order.eventId];
              const payable = isPayable(order, now);
              return (
                <li key={order.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link to={`/events/${order.eventId}`} className="font-semibold hover:underline">
                        {info?.title ?? 'Sự kiện'}
                      </Link>
                      <Badge variant={ORDER_STATUS_BADGE[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
                    </div>
                    <ul className="text-sm text-muted-foreground">
                      {order.items.map((item) => (
                        <li key={item.ticketTypeId}>
                          {item.quantity} × {info?.ticketNames[item.ticketTypeId] ?? 'Vé'} ·{' '}
                          {formatMoney(item.unitPrice)}
                        </li>
                      ))}
                    </ul>
                    <p className="text-xs text-muted-foreground">
                      Mã đơn <code>{order.id.slice(0, 8)}</code> · Đặt lúc {formatDateTime(order.createdAt)}
                    </p>
                    {order.status === 'PENDING' && (
                      <p className="flex items-center gap-1 text-xs font-medium text-warning-foreground">
                        <Clock className="size-3.5" />
                        {payable
                          ? `Giữ vé còn ${remaining(order.expiresAt, now)}`
                          : 'Đã hết thời gian giữ vé, đơn sẽ chuyển sang Hết hạn'}
                      </p>
                    )}
                  </div>
                  <div className="flex shrink-0 flex-col items-start gap-2 sm:items-end">
                    <p className="text-lg font-bold tabular-nums">{formatMoney(order.totalAmount)}</p>
                    {payable && (
                      <div className="flex gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={paying !== null}
                          onClick={() => setCancelling(order)}
                        >
                          Hủy đơn
                        </Button>
                        <Button size="sm" disabled={paying !== null} onClick={() => void pay(order)}>
                          {paying === order.id && <Spinner />}
                          Thanh toán
                        </Button>
                      </div>
                    )}
                    {order.status === 'PAID' && (
                      <Link to="/tickets" className="text-sm font-medium text-accent-foreground hover:underline">
                        Xem vé
                      </Link>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
          <ListPagination meta={orders} onPageChange={setPage} unit="đơn" />
        </Card>
      )}
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
          title: event.status === 'fulfilled' ? event.value.title : null,
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
