import { Ban, Check, Edit2, Eye, MoreVertical, Send, Trash2, X } from 'lucide-react';
import { Link } from 'react-router';

import type { EventAction } from '@/admin/EventActionDialog';
import type { EventItem } from '@/api/events';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const iconButton = 'size-9 rounded-full transition-colors hover:bg-primary/10 hover:text-primary';

export const isEditable = (event: EventItem) => event.status === 'DRAFT' || event.status === 'REJECTED';

type EventRowActionsProps = {
  event: EventItem;
  /** Organizer: xem, sửa, gửi duyệt, hủy, xóa nháp. Admin: xem, duyệt, từ chối, hủy (BR-05). */
  area: 'organizer' | 'admin';
  /** Organizer không quản lý sự kiện này (MEMBER xem sự kiện người khác làm HOST) thì chỉ được xem. */
  manageable?: boolean;
  onAction: (action: EventAction, event: EventItem) => void;
};

/** Thao tác trên một dòng theo EventRowActions của openevent: icon xem, sửa và menu ⋮ cho đổi trạng thái. */
export function EventRowActions({ event, area, manageable = true, onAction }: EventRowActionsProps) {
  const editable = area === 'organizer' && manageable && isEditable(event);
  const items = [
    area === 'organizer' && editable && { action: 'submit' as const, label: 'Gửi duyệt', icon: Send },
    area === 'admin' && event.status === 'PENDING_APPROVAL' && { action: 'approve' as const, label: 'Duyệt', icon: Check },
    area === 'admin' && event.status === 'PENDING_APPROVAL' && { action: 'reject' as const, label: 'Từ chối', icon: X },
  ].filter((item) => item !== false);
  const canCancel = event.status === 'PUBLISHED' && manageable;
  const canDelete = editable;

  return (
    <div className="flex items-center justify-end gap-1 text-muted-foreground">
      <Button variant="ghost" size="icon" className={iconButton} asChild>
        <Link to={`/${area}/events/${event.id}`} aria-label={`Xem ${event.title}`} title="Xem chi tiết">
          <Eye className="size-4" />
        </Link>
      </Button>

      {area === 'organizer' &&
        manageable &&
        (editable ? (
          <Button variant="ghost" size="icon" className={iconButton} asChild>
            <Link to={`/organizer/events/${event.id}/edit`} aria-label={`Chỉnh sửa ${event.title}`} title="Chỉnh sửa">
              <Edit2 className="size-4" />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={iconButton}
            disabled
            aria-label="Chỉ sửa được sự kiện nháp hoặc bị từ chối"
            title="Chỉ sửa được sự kiện nháp hoặc bị từ chối"
          >
            <Edit2 className="size-4" />
          </Button>
        ))}

      {(items.length > 0 || canCancel || canDelete) && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="size-9 rounded-full transition-colors hover:text-foreground"
              aria-label={`Thao tác khác với ${event.title}`}
            >
              <MoreVertical className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-52">
            {items.map(({ action, label, icon: Icon }) => (
              <DropdownMenuItem key={action} onSelect={() => onAction(action, event)}>
                <Icon />
                {label}
              </DropdownMenuItem>
            ))}
            {canCancel && (
              <DropdownMenuItem variant="destructive" onSelect={() => onAction('cancel', event)}>
                <Ban />
                Hủy sự kiện
              </DropdownMenuItem>
            )}
            {canDelete && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onSelect={() => onAction('delete', event)}>
                  <Trash2 />
                  Xóa bản nháp
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  );
}
