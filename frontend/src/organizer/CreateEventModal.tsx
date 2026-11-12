import { type FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import { type Category, createEvent, listCategories, listVenues, type Venue } from '@/api/events';
import { listMyOrganizations, type Organization } from '@/api/organizations';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import {
  addHours,
  EMPTY_FORM,
  type EventFieldErrors,
  type EventForm,
  TITLE_MAX_LENGTH,
  toRequest,
  validateForm,
} from './event-form/eventForm';
import { FieldErrorText } from './event-form/FieldErrorText';

const LABEL = 'mb-1.5 block text-sm font-semibold text-foreground';
const REQUIRED = <span className="text-destructive">*</span>;

const PERSONAL = 'PERSONAL';

type CreateEventModalProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Mở từ trang tổ chức thì chọn sẵn tổ chức đó. */
  defaultOrganizationId?: string;
};

/**
 * Tạo nháp bằng popup theo CreateEventModal của openevent, rồi mở trang sửa để bổ sung mô tả, ảnh bìa.
 * Backend bắt buộc danh mục và địa điểm ngay lúc tạo nên popup có thêm hai ô này.
 */
export function CreateEventModal({ open, onOpenChange, defaultOrganizationId }: CreateEventModalProps) {
  const navigate = useNavigate();
  const [form, setForm] = useState<EventForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<EventFieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [organizationId, setOrganizationId] = useState(PERSONAL);
  const [options, setOptions] = useState<{
    categories: Category[];
    venues: Venue[];
    organizations: Organization[];
  } | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setForm(EMPTY_FORM);
    setErrors({});
    setSubmitError(null);
    setLoadError(null);
    setOrganizationId(defaultOrganizationId ?? PERSONAL);
    Promise.all([listCategories(), listVenues(), listMyOrganizations()])
      .then(([categories, venues, organizations]) =>
        // Chỉ tổ chức mình được tạo sự kiện (BR-14).
        setOptions({ categories, venues, organizations: organizations.filter((item) => item.canCreateEvents) }),
      )
      .catch((err: unknown) => setLoadError(errorMessage(err)));
  }, [open, defaultOrganizationId]);

  const setField = (field: keyof EventForm, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    const clientErrors = validateForm(form);
    setErrors(clientErrors);
    setSubmitError(null);
    if (Object.keys(clientErrors).length > 0) return;
    setSaving(true);
    try {
      const created = await createEvent({
        ...toRequest(form),
        organizationId: organizationId === PERSONAL ? null : organizationId,
      });
      onOpenChange(false);
      toast.success('Đã tạo bản nháp. Bổ sung mô tả, ảnh bìa rồi gửi duyệt.');
      navigate(`/organizer/events/${created.id}/edit`);
    } catch (err) {
      setErrors(fieldErrors(err) as EventFieldErrors);
      setSubmitError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !saving && onOpenChange(next)}>
      <DialogContent className="max-w-lg rounded-xl bg-card shadow-2xl">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">Tạo sự kiện mới</DialogTitle>
            <DialogDescription>
              Điền thông tin cơ bản để tạo bản nháp. Mô tả và ảnh bìa bổ sung ở bước sau.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4 text-sm">
            <div>
              <label htmlFor="create-title" className={LABEL}>
                Tên sự kiện {REQUIRED}
              </label>
              <Input
                id="create-title"
                placeholder="Ví dụ: Workshop React 2026..."
                className="bg-background"
                maxLength={TITLE_MAX_LENGTH}
                aria-invalid={Boolean(errors.title) || undefined}
                value={form.title}
                onChange={(e) => setField('title', e.target.value)}
              />
              <FieldErrorText id="create-title-error" message={errors.title} />
            </div>

            {options && options.organizations.length > 0 && (
              <div>
                <label htmlFor="create-organization" className={LABEL}>
                  Tổ chức
                </label>
                <Select value={organizationId} onValueChange={setOrganizationId}>
                  <SelectTrigger id="create-organization" className="w-full bg-background">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={PERSONAL}>Sự kiện cá nhân</SelectItem>
                    {options.organizations.map((organization) => (
                      <SelectItem key={organization.id} value={organization.id}>
                        {organization.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="mt-1 text-xs text-muted-foreground">Chọn lúc tạo, không đổi được sau đó.</p>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label htmlFor="create-start" className={LABEL}>
                  Bắt đầu {REQUIRED}
                </label>
                <Input
                  id="create-start"
                  type="datetime-local"
                  className="bg-background"
                  aria-invalid={Boolean(errors.startTime) || undefined}
                  value={form.startTime}
                  onChange={(e) => {
                    setField('startTime', e.target.value);
                    // Lần đầu chọn giờ bắt đầu thì gợi ý giờ kết thúc sau 2 tiếng.
                    if (e.target.value && !form.endTime) setField('endTime', addHours(e.target.value, 2));
                  }}
                />
                <FieldErrorText id="create-start-error" message={errors.startTime} />
              </div>
              <div>
                <label htmlFor="create-end" className={LABEL}>
                  Kết thúc {REQUIRED}
                </label>
                <Input
                  id="create-end"
                  type="datetime-local"
                  className="bg-background"
                  aria-invalid={Boolean(errors.endTime) || undefined}
                  min={form.startTime || undefined}
                  value={form.endTime}
                  onChange={(e) => setField('endTime', e.target.value)}
                />
                <FieldErrorText id="create-end-error" message={errors.endTime} />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label htmlFor="create-category" className={LABEL}>
                  Danh mục {REQUIRED}
                </label>
                <Select value={form.categoryId} disabled={!options} onValueChange={(v) => setField('categoryId', v)}>
                  <SelectTrigger id="create-category" className="w-full bg-background" aria-invalid={Boolean(errors.categoryId) || undefined}>
                    <SelectValue placeholder={options ? 'Chọn danh mục' : 'Đang tải…'} />
                  </SelectTrigger>
                  <SelectContent>
                    {options?.categories.map((category) => (
                      <SelectItem key={category.id} value={category.id}>
                        {category.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldErrorText id="create-category-error" message={errors.categoryId} />
              </div>
              <div>
                <label htmlFor="create-venue" className={LABEL}>
                  Địa điểm {REQUIRED}
                </label>
                <Select value={form.venueId} disabled={!options} onValueChange={(v) => setField('venueId', v)}>
                  <SelectTrigger id="create-venue" className="w-full bg-background" aria-invalid={Boolean(errors.venueId) || undefined}>
                    <SelectValue placeholder={options ? 'Chọn địa điểm' : 'Đang tải…'} />
                  </SelectTrigger>
                  <SelectContent>
                    {options?.venues.map((venue) => (
                      <SelectItem key={venue.id} value={venue.id}>
                        {venue.name} · {venue.city}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldErrorText id="create-venue-error" message={errors.venueId} />
              </div>
            </div>

            {options && (options.categories.length === 0 || options.venues.length === 0) && (
              <p className="text-xs text-muted-foreground">
                Chưa có {options.categories.length === 0 ? 'danh mục' : 'địa điểm'} nào. Liên hệ Admin để thêm trước.
              </p>
            )}
            {(loadError || submitError) && (
              <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-destructive">
                {loadError ?? submitError}
              </p>
            )}
          </div>

          <DialogFooter className="mt-2 gap-2 sm:gap-0">
            <Button type="button" variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving || !options}>
              {saving && <Spinner />}
              {saving ? 'Đang tạo…' : 'Tạo sự kiện'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
