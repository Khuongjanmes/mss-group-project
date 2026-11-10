import {
  Ban,
  Check,
  Copy,
  Edit2,
  ExternalLink,
  Eye,
  ListChecks,
  MoreVertical,
  ScanLine,
  Send,
  Ticket,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import { Link } from 'react-router';
import { toast } from 'sonner';

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
  /** Organizer: xem, sửa, gửi duyệt, hủy, xóa nháp, vé, phân công. Admin: xem, duyệt, từ chối, hủy (BR-05). */
  area: 'organizer' | 'admin';
  /** Organizer không quản lý sự kiện này (MEMBER xem sự kiện người khác làm HOST) thì chỉ được xem. */
  manageable?: boolean;
  onAction: (action: EventAction, event: EventItem) => void;
};

/**
 * Thao tác trên một dòng theo EventRowActions của openevent: icon xem, sửa và menu ⋮ gồm đổi trạng thái, lối tắt
 * tới loại vé, phân công, người tham dự, check-in, trang công khai, sao chép mã và xóa nháp.
 */
export function EventRowActions({ event, area, manageable = true, onAction }: EventRowActionsProps) {
  const editable = area === 'organizer' && manageable && isEditable(event);
  const organizerTools = area === 'organizer' && manageable;
  const published = event.status === 'PUBLISHED';
  const detail = `/${area}/events/${event.id}`;
  const statusItems = [
    editable && { action: 'submit' as const, label: 'Gửi duyệt', icon: Send },
    area === 'admin' && event.status === 'PENDING_APPROVAL' && { action: 'approve' as const, label: 'Duyệt', icon: Check },
    area === 'admin' && event.status === 'PENDING_APPROVAL' && { action: 'reject' as const, label: 'Từ chối', icon: X },
  ].filter((item) => item !== false);
  const canCancel = published && manageable;

  const copyId = async () => {
    try {
      await navigator.clipboard.writeText(event.id);
      toast.success('Đã sao chép mã sự kiện');
    } catch {
      toast.error('Không sao chép được mã sự kiện');
    }
  };

  return (
    <div className="flex items-center justify-end gap-1 text-muted-foreground">
      <Button variant="ghost" size="icon" className={iconButton} asChild>
        <Link to={detail} aria-label={`Xem ${event.title}`} title="Xem chi tiết">
          <Eye className="size-4" />
        </Link>
      </Button>

      {organizerTools &&
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
        <DropdownMenuContent align="end" className="w-56">
          {statusItems.map(({ action, label, icon: Icon }) => (
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
          {(statusItems.length > 0 || canCancel) && <DropdownMenuSeparator />}

          {organizerTools && (
            <>
              <DropdownMenuItem asChild>
                <Link to={`${detail}#ticket-types`}>
                  <Ticket />
                  Loại vé
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link to={`${detail}#team`}>
                  <Users />
                  Phân công HOST/STAFF
                </Link>
              </DropdownMenuItem>
              {event.status !== 'DRAFT' && (
                <DropdownMenuItem asChild>
                  <Link to={`${detail}#attendees`}>
                    <ListChecks />
                    Người tham dự
                  </Link>
                </DropdownMenuItem>
              )}
              {published && (
                <DropdownMenuItem asChild>
                  <Link to="/organizer/check-in">
                    <ScanLine />
                    Quét check-in
                  </Link>
                </DropdownMenuItem>
              )}
            </>
          )}
          {published && (
            <DropdownMenuItem asChild>
              <Link to={`/events/${event.id}`} target="_blank" rel="noreferrer">
                <ExternalLink />
                Mở trang công khai
              </Link>
            </DropdownMenuItem>
          )}
          <DropdownMenuItem onSelect={() => void copyId()}>
            <Copy />
            Sao chép mã sự kiện
          </DropdownMenuItem>

          {editable && (
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
    </div>
  );
}
