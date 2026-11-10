import { useEffect, useState } from 'react';

import { formatMoney } from '@/api/payments';
import { listTicketTypes, saleState, type TicketType } from '@/api/tickets';
import { formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

const STATE_TEXT = {
  upcoming: (type: TicketType) => `Mở bán ${formatDateTime(type.saleStart)}`,
  ended: () => 'Đã đóng bán',
  soldOut: () => 'Hết vé',
  onSale: (type: TicketType) => `Còn ${type.available} vé`,
};

/** Trang công khai: loại vé, giá và số vé còn (chức năng #4). Đặt vé đi qua Order. */
export function TicketTypeList({ eventId }: { eventId: string }) {
  const [types, setTypes] = useState<TicketType[] | null>(null);

  useEffect(() => {
    let ignore = false;
    // Lỗi tải vé không làm hỏng trang chi tiết: chỉ ẩn phần vé.
    listTicketTypes(eventId)
      .then((data) => !ignore && setTypes(data))
      .catch(() => !ignore && setTypes([]));
    return () => {
      ignore = true;
    };
  }, [eventId]);

  if (!types) return <p className="text-sm text-muted-foreground">Đang tải loại vé…</p>;
  if (types.length === 0) return <p className="text-sm text-muted-foreground">Ban tổ chức chưa mở bán vé.</p>;

  return (
    <div className="space-y-2">
      <p className="text-[11px] font-bold tracking-wider text-muted-foreground uppercase">Loại vé</p>
      <ul className="space-y-2">
        {types.map((type) => {
          const state = saleState(type);
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
              </div>
              <p className="shrink-0 font-bold tabular-nums">{type.price === 0 ? 'Miễn phí' : formatMoney(type.price)}</p>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
