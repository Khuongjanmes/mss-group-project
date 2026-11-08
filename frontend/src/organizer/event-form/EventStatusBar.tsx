import { Ban, Check, Send, X } from 'lucide-react';
import { useState } from 'react';

import type { EventItem, EventStatus } from '@/api/events';
import { hasRole, useSession } from '@/auth/session';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/datetime';
import { EVENT_STATUS_BADGE, EVENT_STATUS_LABELS } from '@/lib/eventStatus';
import { type EventAction, EventActionDialog } from '@/admin/EventActionDialog';

const STATUS_TEXT: Record<EventStatus, string> = {
  DRAFT: 'Bản nháp: chỉ bạn và Admin thấy. Gửi duyệt để được công bố.',
  REJECTED: 'Bị từ chối: sửa theo lý do bên dưới rồi gửi duyệt lại.',
  PENDING_APPROVAL: 'Đang chờ duyệt: không sửa được cho tới khi có kết quả.',
  PUBLISHED: 'Đã công bố: mọi người đang thấy sự kiện này, nội dung không sửa được nữa.',
  CANCELLED: 'Sự kiện đã hủy: chỉ xem, không chỉnh sửa được.',
  COMPLETED: 'Sự kiện đã kết thúc: chỉ xem, không chỉnh sửa được.',
};

type EventStatusBarProps = {
  event: EventItem;
  /** Thay đổi chưa lưu không được gửi duyệt cùng. */
  hasUnsavedChanges: boolean;
  onChanged: () => void;
};

/** Trạng thái và các bước chuyển trạng thái của trang sửa sự kiện (BR-05). */
export function EventStatusBar({ event, hasUnsavedChanges, onChanged }: EventStatusBarProps) {
  const session = useSession();
  const [action, setAction] = useState<EventAction | null>(null);
  const isAdmin = hasRole(session, 'ADMIN');
  const canSubmit = event.status === 'DRAFT' || event.status === 'REJECTED';

  return (
    <section aria-label="Trạng thái sự kiện" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <Badge variant={EVENT_STATUS_BADGE[event.status]} size="label">
            {EVENT_STATUS_LABELS[event.status]}
          </Badge>
          <span>{STATUS_TEXT[event.status]}</span>
          <span>· Tạo lúc {formatDateTime(event.createdAt)}</span>
        </div>
        <div className="flex flex-wrap gap-2">
          {canSubmit && (
            <Button
              type="button"
              disabled={hasUnsavedChanges}
              title={hasUnsavedChanges ? 'Lưu thay đổi trước khi gửi duyệt' : undefined}
              onClick={() => setAction('submit')}
            >
              <Send />
              Gửi duyệt
            </Button>
          )}
          {event.status === 'PENDING_APPROVAL' && isAdmin && (
            <>
              <Button type="button" onClick={() => setAction('approve')}>
                <Check />
                Duyệt
              </Button>
              <Button type="button" variant="outline" onClick={() => setAction('reject')}>
                <X />
                Từ chối
              </Button>
            </>
          )}
          {event.status === 'PUBLISHED' && (
            <Button type="button" variant="destructive" onClick={() => setAction('cancel')}>
              <Ban />
              Hủy sự kiện
            </Button>
          )}
        </div>
      </div>

      {canSubmit && hasUnsavedChanges && (
        <p className="text-xs text-muted-foreground">Có thay đổi chưa lưu: lưu trước khi gửi duyệt.</p>
      )}

      {event.status === 'REJECTED' && event.rejectReason && (
        <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          <p className="font-semibold">Lý do bị từ chối</p>
          <p className="mt-1 whitespace-pre-line">{event.rejectReason}</p>
        </div>
      )}

      {action && (
        <EventActionDialog
          action={action}
          event={event}
          onClose={() => setAction(null)}
          onDone={() => {
            setAction(null);
            onChanged();
          }}
        />
      )}
    </section>
  );
}
