import { ArrowLeft, Ban, Check, Edit2, Send, Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import { type EventAction, EventActionDialog } from '@/admin/EventActionDialog';
import { errorMessage } from '@/api/client';
import { type EventItem, getEvent } from '@/api/events';
import { canManageEvent } from '@/api/organizations';
import { useSession } from '@/auth/session';
import { ErrorState, LoadingState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/datetime';
import { useMyOrganizations } from '@/organizer/useMyOrganizations';
import { EventDetailView } from './EventDetailView';
import { AttendeesCard } from './AttendeesCard';
import { EventTeamCard } from './EventTeamCard';
import { TicketTypesCard } from './TicketTypesCard';
import { isEditable } from './EventRowActions';

const STATUS_TEXT: Record<EventItem['status'], string> = {
  DRAFT: 'Bản nháp: chỉ ban tổ chức và Admin thấy. Gửi duyệt để được công bố.',
  REJECTED: 'Bị từ chối: sửa theo lý do bên dưới rồi gửi duyệt lại.',
  PENDING_APPROVAL: 'Đang chờ Admin duyệt: không sửa được cho tới khi có kết quả.',
  PUBLISHED: 'Đã công bố: mọi người đang thấy sự kiện này.',
  CANCELLED: 'Sự kiện đã hủy: chỉ xem.',
  COMPLETED: 'Sự kiện đã kết thúc: chỉ xem.',
};

/**
 * Xem sự kiện đúng như người mua vé sẽ thấy (theo EventDetailPage của openevent), kèm thao tác của người xem:
 * Organizer sửa, gửi duyệt, xóa, hủy; Admin duyệt, từ chối, hủy.
 */
export default function EventManageDetailPage({ area }: { area: 'organizer' | 'admin' }) {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [event, setEvent] = useState<EventItem | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [action, setAction] = useState<EventAction | null>(null);
  const listPath = `/${area}/events`;
  const session = useSession();
  const organizations = useMyOrganizations(area === 'organizer');
  const { hash } = useLocation();

  useEffect(() => {
    let ignore = false;
    setError(null);
    getEvent(id)
      .then((data) => !ignore && setEvent(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [id, reloadKey]);

  // Menu ⋮ ở danh sách dẫn tới #ticket-types, #team, #attendees: cuộn tới khi thẻ đã hiện.
  useEffect(() => {
    if (event && hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' });
  }, [event, hash]);

  if (error) {
    return <ErrorState title="Không thể tải sự kiện" description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  }
  if (!event) return <LoadingState lines={10} />;

  const manageable = area === 'admin' || canManageEvent(event, session?.user.id, organizations);
  const editable = area === 'organizer' && manageable && isEditable(event);
  const actions = (
    <div className="flex flex-col gap-2">
      {editable && (
        <>
          <Button asChild variant="outline" className="w-full">
            <Link to={`/organizer/events/${event.id}/edit`}>
              <Edit2 />
              Chỉnh sửa
            </Link>
          </Button>
          <Button className="w-full" onClick={() => setAction('submit')}>
            <Send />
            Gửi duyệt
          </Button>
          <Button variant="ghost" className="w-full text-destructive hover:text-destructive" onClick={() => setAction('delete')}>
            <Trash2 />
            Xóa bản nháp
          </Button>
        </>
      )}
      {area === 'admin' && event.status === 'PENDING_APPROVAL' && (
        <>
          <Button className="w-full" onClick={() => setAction('approve')}>
            <Check />
            Duyệt và công bố
          </Button>
          <Button variant="outline" className="w-full" onClick={() => setAction('reject')}>
            <X />
            Từ chối
          </Button>
        </>
      )}
      {event.status === 'PUBLISHED' && manageable && (
        <Button variant="destructive" className="w-full" onClick={() => setAction('cancel')}>
          <Ban />
          Hủy sự kiện
        </Button>
      )}
    </div>
  );
  const hasActions = editable || (event.status === 'PUBLISHED' && manageable) || (area === 'admin' && event.status === 'PENDING_APPROVAL');

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button asChild variant="ghost" size="sm" className="-ml-2 text-muted-foreground">
          <Link to={listPath}>
            <ArrowLeft />
            Danh sách sự kiện
          </Link>
        </Button>
        <p className="text-sm text-muted-foreground">
          {STATUS_TEXT[event.status]} · Tạo lúc {formatDateTime(event.createdAt)}
        </p>
      </div>

      {event.status === 'REJECTED' && event.rejectReason && (
        <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          <p className="font-semibold">Lý do bị từ chối</p>
          <p className="mt-1 whitespace-pre-line">{event.rejectReason}</p>
        </div>
      )}

      {area === 'organizer' && manageable && (
        <>
          <EventTeamCard event={event} onHostChanged={() => setReloadKey((k) => k + 1)} />
          <TicketTypesCard event={event} />
          {event.status !== 'DRAFT' && <AttendeesCard eventId={event.id} />}
        </>
      )}

      <EventDetailView event={event} aside={hasActions ? actions : undefined} />

      {action && (
        <EventActionDialog
          action={action}
          event={event}
          onClose={() => setAction(null)}
          onDone={() => {
            setAction(null);
            if (action === 'delete') navigate(listPath, { replace: true });
            else setReloadKey((k) => k + 1);
          }}
        />
      )}
    </div>
  );
}
