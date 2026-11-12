import { Pencil, Plus, Ticket as TicketIcon, Trash2 } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import type { EventItem } from '@/api/events';
import { formatMoney } from '@/api/payments';
import {
  createTicketType,
  deleteTicketType,
  listTicketTypes,
  type TicketType,
  type TicketTypeStats,
  ticketStats,
  updateTicketType,
} from '@/api/tickets';
import { ConfirmDeleteDialog } from '@/admin/ConfirmDeleteDialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { formatDateTime, fromLocalInput, toLocalInput } from '@/lib/datetime';

const TITLE = 'flex items-center gap-2 text-[11px] font-bold tracking-wider text-foreground uppercase';

/** Organizer #4: loại vé của sự kiện kèm số đã bán, đang giữ, đã check-in. Sự kiện đã hủy hay kết thúc thì chỉ xem. */
export function TicketTypesCard({ event }: { event: EventItem }) {
  const [types, setTypes] = useState<TicketType[] | null>(null);
  const [stats, setStats] = useState<Record<string, TicketTypeStats>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<TicketType | 'new' | null>(null);
  const [deleting, setDeleting] = useState<TicketType | null>(null);
  const closed = event.status === 'CANCELLED' || event.status === 'COMPLETED';

  useEffect(() => {
    let ignore = false;
    setError(null);
    Promise.all([listTicketTypes(event.id), ticketStats(event.id)])
      .then(([list, counts]) => {
        if (ignore) return;
        setTypes(list);
        setStats(Object.fromEntries(counts.map((row) => [row.id, row])));
      })
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [event.id, reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);

  return (
    <Card id="ticket-types" className="scroll-mt-20 gap-0 rounded-xl py-0 shadow-sm">
      <CardContent className="space-y-4 p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 className={TITLE}>
            <TicketIcon className="size-4 text-accent-foreground" aria-hidden />
            Loại vé
          </h2>
          {!closed && (
            <Button size="sm" variant="outline" onClick={() => setEditing('new')}>
              <Plus />
              Thêm loại vé
            </Button>
          )}
        </div>

        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : !types ? (
          <p className="text-sm text-muted-foreground">Đang tải…</p>
        ) : types.length === 0 ? (
          <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
            Chưa có loại vé. Thêm ít nhất một loại (ví dụ Thường, VIP) để người mua đặt vé sau khi sự kiện được duyệt.
          </p>
        ) : (
          <ul className="divide-y rounded-lg border">
            {types.map((type) => {
              const counts = stats[type.id];
              return (
                <li key={type.id} className="flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 text-sm">
                  <div className="min-w-40 flex-1">
                    <p className="font-semibold">{type.name}</p>
                    <p className="text-xs text-muted-foreground">
                      Mở bán {formatDateTime(type.saleStart)} – {formatDateTime(type.saleEnd)}
                    </p>
                  </div>
                  <p className="font-semibold tabular-nums">{type.price === 0 ? 'Miễn phí' : formatMoney(type.price)}</p>
                  <p className="text-muted-foreground tabular-nums">
                    Đã bán <strong className="text-foreground">{type.soldQuantity}</strong>/{type.totalQuantity}
                    {type.reservedQuantity > 0 && ` · đang giữ ${type.reservedQuantity}`}
                    {counts && ` · check-in ${counts.checkedIn}`}
                  </p>
                  {!closed && (
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" aria-label={`Sửa ${type.name}`} onClick={() => setEditing(type)}>
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive hover:text-destructive"
                        aria-label={`Xóa ${type.name}`}
                        onClick={() => setDeleting(type)}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>

      {editing && (
        <TicketTypeDialog
          event={event}
          type={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={reload}
        />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          title={`Xóa loại vé "${deleting.name}"?`}
          description="Chỉ xóa được loại vé chưa có ai giữ hoặc mua. Loại đã bán thì giảm số lượng hoặc đóng mở bán."
          onConfirm={async () => {
            await deleteTicketType(deleting.id);
            toast.success(`Đã xóa loại vé "${deleting.name}"`);
            reload();
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </Card>
  );
}

const LABEL = 'block font-semibold';

function TicketTypeDialog({
  event,
  type,
  onClose,
  onSaved,
}: {
  event: EventItem;
  type?: TicketType;
  onClose: () => void;
  onSaved: () => void;
}) {
  // Mặc định mở bán từ bây giờ tới 1 giờ trước khi sự kiện bắt đầu (BR-07).
  const defaultEnd = new Date(Date.parse(event.startTime) - 3_600_000).toISOString();
  const [form, setForm] = useState({
    name: type?.name ?? '',
    price: type ? String(type.price) : '',
    totalQuantity: type ? String(type.totalQuantity) : '',
    saleStart: toLocalInput(type?.saleStart ?? new Date().toISOString()),
    saleEnd: toLocalInput(type?.saleEnd ?? defaultEnd),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: '' }));
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    const price = Number(form.price);
    const quantity = Number(form.totalQuantity);
    if (!form.name.trim()) next.name = 'Nhập tên loại vé';
    if (form.price.trim() === '' || !Number.isInteger(price) || price < 0) next.price = 'Giá là số nguyên không âm (VND)';
    if (!Number.isInteger(quantity) || quantity < 1) next.totalQuantity = 'Số lượng ít nhất là 1';
    if (!form.saleStart || !form.saleEnd) next.saleEnd = 'Chọn thời gian mở bán';
    else if (Date.parse(fromLocalInput(form.saleEnd)) <= Date.parse(fromLocalInput(form.saleStart)))
      next.saleEnd = 'Kết thúc phải sau bắt đầu';
    else if (Date.parse(fromLocalInput(form.saleEnd)) > Date.parse(event.startTime))
      next.saleEnd = 'Mở bán phải kết thúc trước khi sự kiện bắt đầu';
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSaving(true);
    setError(null);
    try {
      const body = {
        eventId: event.id,
        name: form.name.trim(),
        price,
        totalQuantity: quantity,
        saleStart: fromLocalInput(form.saleStart),
        saleEnd: fromLocalInput(form.saleEnd),
      };
      if (type) await updateTicketType(type.id, body);
      else await createTicketType(body);
      toast.success(type ? 'Đã lưu loại vé' : `Đã thêm loại vé "${body.name}"`);
      onSaved();
      onClose();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const field = (key: keyof typeof form, label: string, props: Record<string, unknown>) => (
    <div className="space-y-1.5">
      <label htmlFor={`ticket-type-${key}`} className={LABEL}>
        {label} <span className="text-destructive">*</span>
      </label>
      <Input
        id={`ticket-type-${key}`}
        aria-invalid={Boolean(errors[key]) || undefined}
        value={form[key]}
        onChange={(e) => set(key, e.target.value)}
        {...props}
      />
      {errors[key] && <p className="text-xs text-destructive">{errors[key]}</p>}
    </div>
  );

  return (
    <Dialog open onOpenChange={(open) => !open && !saving && onClose()}>
      <DialogContent className="max-w-lg">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle>{type ? 'Sửa loại vé' : 'Thêm loại vé'}</DialogTitle>
            <DialogDescription>
              Không giảm được số lượng xuống dưới số vé đã bán và đang giữ. Giá mới chỉ áp dụng cho đơn đặt sau.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4 text-sm">
            {field('name', 'Tên loại vé', { autoFocus: true, maxLength: 100, placeholder: 'Ví dụ: VIP, Early Bird' })}
            <div className="grid grid-cols-2 gap-4">
              {field('price', 'Giá (VND)', { type: 'number', min: 0, step: 1000, placeholder: '0 = miễn phí' })}
              {field('totalQuantity', 'Số lượng', { type: 'number', min: 1, step: 1 })}
            </div>
            <div className="grid grid-cols-2 gap-4">
              {field('saleStart', 'Mở bán từ', { type: 'datetime-local' })}
              {field('saleEnd', 'Đến', { type: 'datetime-local' })}
            </div>
            {error && (
              <p role="alert" className="font-medium text-destructive">
                {error}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={saving} onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving && <Spinner />}
              {type ? 'Lưu' : 'Thêm loại vé'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
