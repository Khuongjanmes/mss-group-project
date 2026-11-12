import { Minus, Plus } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { createOrder, MAX_TICKETS_PER_EVENT, payOrder } from '@/api/orders';
import { formatMoney } from '@/api/payments';
import { listTicketTypes, saleState, type TicketType } from '@/api/tickets';
import { hasRole, useSession } from '@/auth/session';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

const STATE_TEXT = {
  upcoming: (type: TicketType) => `Mở bán ${formatDateTime(type.saleStart)}`,
  ended: () => 'Đã đóng bán',
  soldOut: () => 'Hết vé',
  onSale: (type: TicketType) => `Còn ${type.available} vé`,
};

/**
 * Trang công khai: loại vé, giá, số vé còn (chức năng #4) và đặt vé (#6). Đặt vé tạo đơn ở Order rồi chuyển sang cổng
 * thanh toán; giá do server lấy từ Ticket, client chỉ gửi loại vé và số lượng.
 */
export function TicketTypeList({ eventId }: { eventId: string }) {
  const session = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [types, setTypes] = useState<TicketType[] | null>(null);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    let ignore = false;
    // Lỗi tải vé không làm hỏng trang chi tiết: chỉ ẩn phần vé.
    listTicketTypes(eventId)
      .then((data) => !ignore && setTypes(data))
      .catch(() => !ignore && setTypes([]));
    return () => {
      ignore = true;
    };
  }, [eventId]);

  useEffect(load, [load]);

  if (!types) return <p className="text-sm text-muted-foreground">Đang tải loại vé…</p>;
  if (types.length === 0) return <p className="text-sm text-muted-foreground">Ban tổ chức chưa mở bán vé.</p>;

  // Admin không mua vé (thanh điều hướng cũng không có "Vé của tôi" cho admin).
  const canBuy = !hasRole(session, 'ADMIN');
  const selected = types.filter((type) => (quantities[type.id] ?? 0) > 0);
  const ticketCount = selected.reduce((sum, type) => sum + quantities[type.id], 0);
  const total = selected.reduce((sum, type) => sum + type.price * quantities[type.id], 0);

  function change(type: TicketType, delta: number) {
    setQuantities((current) => ({ ...current, [type.id]: Math.max(0, (current[type.id] ?? 0) + delta) }));
  }

  async function book() {
    if (!session) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`);
      return;
    }
    setSubmitting(true);
    let orderId: string;
    try {
      const order = await createOrder(
        eventId,
        selected.map((type) => ({ ticketTypeId: type.id, quantity: quantities[type.id] })),
      );
      orderId = order.id;
    } catch (err) {
      // Hết vé, vượt 5 vé, chưa mở bán…: thông báo của server đã rõ; tải lại số vé còn.
      toast.error(errorMessage(err));
      setSubmitting(false);
      load();
      return;
    }
    try {
      await payOrder(orderId);
    } catch (err) {
      // Vé đã được giữ 10 phút: cho người mua thanh toán lại từ "Đơn của tôi".
      toast.error(`Đã giữ vé nhưng chưa mở được cổng thanh toán: ${errorMessage(err)}`);
      navigate('/orders');
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-[11px] font-bold tracking-wider text-muted-foreground uppercase">Loại vé</p>
      <ul className="space-y-2">
        {types.map((type) => {
          const state = saleState(type);
          const quantity = quantities[type.id] ?? 0;
          const canAdd = quantity < type.available && ticketCount < MAX_TICKETS_PER_EVENT;
          return (
            <li
              key={type.id}
              className={cn('flex items-center justify-between gap-3 rounded-lg border p-3', state !== 'onSale' && 'opacity-70')}
            >
              <div className="min-w-0">
                <p className="truncate font-semibold">{type.name}</p>
                <p className={cn('text-xs', state === 'soldOut' ? 'text-destructive' : 'text-muted-foreground')}>
                  {STATE_TEXT[state](type)}
                </p>
                <p className="font-bold tabular-nums">{type.price === 0 ? 'Miễn phí' : formatMoney(type.price)}</p>
              </div>
              {canBuy && state === 'onSale' && (
                <div className="flex shrink-0 items-center gap-1">
                  <Button
                    variant="outline"
                    size="icon-sm"
                    aria-label={`Bớt một vé ${type.name}`}
                    disabled={quantity === 0 || submitting}
                    onClick={() => change(type, -1)}
                  >
                    <Minus />
                  </Button>
                  <span className="w-6 text-center font-semibold tabular-nums" aria-live="polite">
                    {quantity}
                  </span>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    aria-label={`Thêm một vé ${type.name}`}
                    disabled={!canAdd || submitting}
                    onClick={() => change(type, 1)}
                  >
                    <Plus />
                  </Button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
      {canBuy && types.some((type) => saleState(type) === 'onSale') && (
        <div className="space-y-2 border-t pt-3">
          <div className="flex items-baseline justify-between gap-3">
            <span className="text-sm text-muted-foreground">
              {ticketCount} vé · tối đa {MAX_TICKETS_PER_EVENT} vé mỗi sự kiện
            </span>
            <span className="text-lg font-bold tabular-nums">{formatMoney(total)}</span>
          </div>
          <Button className="w-full" disabled={submitting || (session !== null && ticketCount === 0)} onClick={() => void book()}>
            {submitting && <Spinner />}
            {session ? 'Đặt vé' : 'Đăng nhập để đặt vé'}
          </Button>
          <p className="text-xs text-muted-foreground">Vé được giữ 10 phút để bạn thanh toán.</p>
        </div>
      )}
    </div>
  );
}
