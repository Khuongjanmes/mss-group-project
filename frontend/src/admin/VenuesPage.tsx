import { MapPin, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import { createVenue, deleteVenue, searchVenues, updateVenue, type Venue } from '@/api/events';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { ListPagination } from '@/components/common/navigation';
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';
import { ConfirmDeleteDialog } from './ConfirmDeleteDialog';

/** Địa điểm dùng chung cho mọi sự kiện, theo trang Địa điểm tổ chức của openevent. Chỉ Admin thêm, sửa, xóa. */
export default function VenuesPage() {
  const [cityInput, setCityInput] = useState('');
  const [city, setCity] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [result, setResult] = useState<PageResponse<Venue> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<Venue | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Venue | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    searchVenues({ city: city || undefined, page, size })
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [city, page, size, reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);
  const addButton = (
    <Button onClick={() => setEditing('new')}>
      <Plus />
      Thêm địa điểm
    </Button>
  );

  return (
    <div className="space-y-6 text-sm">
      <PageHeader
        title="Địa điểm tổ chức"
        description="Hội trường, phòng, sân khấu để ban tổ chức chọn cho sự kiện. Địa điểm đang có sự kiện dùng thì không xóa được."
        actions={addButton}
      />

      <form
        role="search"
        className="flex w-full max-w-sm gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setCity(cityInput.trim());
          setPage(0);
        }}
      >
        <div className="relative flex-1">
          <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            type="search"
            aria-label="Lọc theo thành phố"
            placeholder="Lọc theo thành phố, ví dụ Đà Nẵng"
            className="pl-9"
            maxLength={100}
            value={cityInput}
            onChange={(e) => setCityInput(e.target.value)}
          />
        </div>
        <Button type="submit" variant="outline">
          Lọc
        </Button>
      </form>

      <Card className="py-0">
        <CardContent className="px-0">
          {error ? (
            <ErrorState description={error} onRetry={reload} />
          ) : !result ? (
            <div className="p-6">
              <LoadingState lines={5} />
            </div>
          ) : result.content.length === 0 ? (
            city ? (
              <EmptyState title="Không có địa điểm ở thành phố này" description="Lọc theo thành phố khác hoặc xóa bộ lọc." />
            ) : (
              <EmptyState
                title="Chưa có địa điểm nào"
                description="Sự kiện cần có địa điểm mới tạo được. Hãy thêm địa điểm đầu tiên."
                action={addButton}
              />
            )
          ) : (
            <>
              <Table aria-busy={loading} className={cn(loading && 'opacity-60')}>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">Tên</TableHead>
                    <TableHead>Địa chỉ</TableHead>
                    <TableHead>Thành phố</TableHead>
                    <TableHead className="text-right">Sức chứa</TableHead>
                    <TableHead className="pr-6 text-right">
                      <span className="sr-only">Thao tác</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((venue) => (
                    <TableRow key={venue.id}>
                      <TableCell className="pl-6 font-medium">
                        <span className="flex items-center gap-2">
                          <MapPin className="size-4 shrink-0 text-primary/70" aria-hidden />
                          {venue.name}
                        </span>
                      </TableCell>
                      <TableCell className="max-w-80 truncate text-muted-foreground" title={venue.address}>
                        {venue.address}
                      </TableCell>
                      <TableCell>{venue.city}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {venue.capacity ? venue.capacity.toLocaleString('vi-VN') : '—'}
                      </TableCell>
                      <TableCell className="pr-6">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" aria-label={`Sửa ${venue.name}`} onClick={() => setEditing(venue)}>
                            <Pencil />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive hover:text-destructive"
                            aria-label={`Xóa ${venue.name}`}
                            onClick={() => setDeleting(venue)}
                          >
                            <Trash2 />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <ListPagination
                meta={result}
                unit="địa điểm"
                onPageChange={setPage}
                pageSizes={[10, 20, 50]}
                onPageSizeChange={(next) => {
                  setSize(next);
                  setPage(0);
                }}
              />
            </>
          )}
        </CardContent>
      </Card>

      {editing && (
        <VenueFormDialog venue={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={reload} />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          title={`Xóa địa điểm "${deleting.name}"?`}
          description="Xóa rồi không khôi phục được. Địa điểm đang có sự kiện dùng thì không xóa được."
          onConfirm={async () => {
            await deleteVenue(deleting.id);
            toast.success(`Đã xóa địa điểm "${deleting.name}"`);
            reload();
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  );
}

const LABEL = 'block font-semibold';

function VenueFormDialog({ venue, onClose, onSaved }: { venue?: Venue; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    name: venue?.name ?? '',
    address: venue?.address ?? '',
    city: venue?.city ?? '',
    capacity: venue?.capacity ? String(venue.capacity) : '',
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
    if (!form.name.trim()) next.name = 'Nhập tên địa điểm';
    if (!form.address.trim()) next.address = 'Nhập địa chỉ';
    if (!form.city.trim()) next.city = 'Nhập thành phố';
    const capacity = form.capacity.trim() ? Number(form.capacity) : null;
    if (capacity !== null && (!Number.isInteger(capacity) || capacity <= 0)) next.capacity = 'Sức chứa là số nguyên dương';
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSaving(true);
    setError(null);
    try {
      const body = { name: form.name.trim(), address: form.address.trim(), city: form.city.trim(), capacity };
      if (venue) await updateVenue(venue.id, body);
      else await createVenue(body);
      toast.success(venue ? 'Đã lưu địa điểm' : `Đã thêm địa điểm "${body.name}"`);
      onSaved();
      onClose();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const field = (key: keyof typeof form, label: string, props: Record<string, unknown>, required = true) => (
    <div className="space-y-1.5">
      <label htmlFor={`venue-${key}`} className={LABEL}>
        {label} {required && <span className="text-destructive">*</span>}
      </label>
      <Input
        id={`venue-${key}`}
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
      <DialogContent className="max-w-md">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle>{venue ? 'Sửa địa điểm' : 'Thêm địa điểm'}</DialogTitle>
            <DialogDescription>Nên đặt tên chỗ cụ thể (Hội trường A, Sân khấu ngoài trời) để ban tổ chức dễ chọn.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4 text-sm">
            {field('name', 'Tên địa điểm', { autoFocus: true, maxLength: 200, placeholder: 'Ví dụ: Hội trường FPT' })}
            {field('address', 'Địa chỉ', { maxLength: 300, placeholder: 'Số nhà, đường, phường' })}
            <div className="grid grid-cols-2 gap-4">
              {field('city', 'Thành phố', { maxLength: 100, placeholder: 'Đà Nẵng' })}
              {field('capacity', 'Sức chứa', { type: 'number', min: 1, step: 1, placeholder: 'Không giới hạn' }, false)}
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
              {venue ? 'Lưu' : 'Thêm địa điểm'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
