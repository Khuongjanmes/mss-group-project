import { Send } from 'lucide-react';
import { type FormEvent, type ReactNode, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import {
  type Category,
  type EventItem,
  getEvent,
  listCategories,
  listVenues,
  submitEvent,
  updateEvent,
  type Venue,
} from '@/api/events';
import { canManageEvent } from '@/api/organizations';
import { useSession } from '@/auth/session';
import { ErrorState, LoadingState, UnsavedChangesDialog } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { useUnsavedChangesGuard } from '@/lib/useUnsavedChangesGuard';
import { EventGeneralInfoCard } from './event-form/EventGeneralInfoCard';
import { EventMediaCard } from './event-form/EventMediaCard';
import { EventStatusBar } from './event-form/EventStatusBar';
import { EventTimeLocationCard } from './event-form/EventTimeLocationCard';
import { useMyOrganizations } from './useMyOrganizations';
import {
  EMPTY_FORM,
  type EventFieldErrors,
  type EventForm,
  isSameForm,
  toForm,
  toRequest,
  validateForm,
} from './event-form/eventForm';

/**
 * Sửa sự kiện (/organizer/events/:id/edit) theo trang "Cập nhật sự kiện" của openevent: thanh trạng thái, các thẻ
 * thông tin, thanh lưu cố định ở đáy. Tạo mới dùng {@link CreateEventModal} rồi chuyển sang đây.
 */
export default function EventFormPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const session = useSession();
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [venues, setVenues] = useState<Venue[] | null>(null);
  const [event, setEvent] = useState<EventItem | null>(null);
  const [saved, setSaved] = useState<EventForm>(EMPTY_FORM);
  const [form, setForm] = useState<EventForm>(EMPTY_FORM);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [errors, setErrors] = useState<EventFieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState<'draft' | 'submit' | null>(null);

  const organizations = useMyOrganizations();
  const isOwner = Boolean(event) && canManageEvent(event as EventItem, session?.user.id, organizations);
  const editable = Boolean(event) && isOwner && (event?.status === 'DRAFT' || event?.status === 'REJECTED');
  const dirty = editable && !isSameForm(form, saved);
  const { blocker, allowNextNavigation } = useUnsavedChangesGuard(dirty);

  useEffect(() => {
    let ignore = false;
    setLoadError(null);
    Promise.all([listCategories(), listVenues(), getEvent(id)])
      .then(([categoryList, venueList, loaded]) => {
        if (ignore) return;
        setCategories(categoryList);
        setVenues(venueList);
        setEvent(loaded);
        const next = toForm(loaded);
        setForm(next);
        setSaved(next);
      })
      .catch((err: unknown) => !ignore && setLoadError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [id, reloadKey]);

  const setField = (field: keyof EventForm, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  async function save(mode: 'draft' | 'submit') {
    const clientErrors = validateForm(form);
    setErrors(clientErrors);
    setSubmitError(null);
    if (Object.keys(clientErrors).length > 0) return;

    setSaving(mode);
    try {
      const result = await updateEvent(id, toRequest(form));
      const next = toForm(result);
      setEvent(result);
      setForm(next);
      setSaved(next);
      if (mode === 'submit') {
        await submitEvent(id);
        toast.success('Đã lưu và gửi duyệt');
        allowNextNavigation();
        navigate(`/organizer/events/${id}`);
        return;
      }
      toast.success('Đã lưu thay đổi');
    } catch (err) {
      setErrors(fieldErrors(err) as EventFieldErrors);
      setSubmitError(errorMessage(err));
    } finally {
      setSaving(null);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void save('draft');
  }

  if (loadError) {
    return (
      <ErrorState
        title="Không thể tải sự kiện"
        description={loadError}
        onRetry={() => setReloadKey((key) => key + 1)}
      />
    );
  }
  if (!categories || !venues || !event) return <LoadingState lines={10} />;

  const busy = saving !== null;

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-6 pb-28">
      <UnsavedChangesDialog blocker={blocker} />
      <PageHeader title="Sửa sự kiện" description={event.title} />

      <EventStatusBar event={event} hasUnsavedChanges={dirty} onChanged={() => setReloadKey((key) => key + 1)} />

      {!isOwner && <Notice>Bạn chỉ xem được sự kiện này: chỉ HOST hoặc Chủ sở hữu, Quản lý của tổ chức mới sửa được.</Notice>}

      <EventGeneralInfoCard
        form={form}
        setField={setField}
        categories={categories}
        errors={errors}
        disabled={!editable}
      />
      <EventTimeLocationCard form={form} setField={setField} venues={venues} errors={errors} disabled={!editable} />
      <EventMediaCard eventId={id} form={form} setField={setField} errors={errors} disabled={!editable} />

      {submitError && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {submitError}
        </p>
      )}

      <div
        data-save-bar
        className="fixed right-0 bottom-0 left-0 z-50 border-t bg-background/80 p-4 backdrop-blur transition-[left] duration-200 ease-linear lg:left-[var(--dashboard-content-left)]"
      >
        <div className="mx-auto flex max-w-[1400px] justify-end gap-3">
          <Button type="button" variant="outline" asChild>
            <Link to={`/organizer/events/${id}`}>{editable ? 'Hủy' : 'Quay lại'}</Link>
          </Button>
          {editable && (
            <>
              <Button type="submit" variant="outline" disabled={busy || !dirty}>
                {saving === 'draft' && <Spinner />}
                {saving === 'draft' ? 'Đang lưu…' : 'Lưu thay đổi'}
              </Button>
              <Button type="button" disabled={busy} onClick={() => void save('submit')}>
                {saving === 'submit' ? <Spinner /> : <Send />}
                {saving === 'submit' ? 'Đang gửi…' : 'Lưu và gửi duyệt'}
              </Button>
            </>
          )}
        </div>
      </div>
    </form>
  );
}

function Notice({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-lg border border-primary/30 bg-accent p-4 text-sm font-medium text-accent-foreground">
      {children}
    </div>
  );
}
