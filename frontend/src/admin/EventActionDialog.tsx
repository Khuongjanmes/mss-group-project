import { useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import {
  approveEvent,
  cancelEvent,
  deleteEvent,
  type EventItem,
  rejectEvent,
  submitEvent,
} from '@/api/events';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';
import { Textarea } from '@/components/ui/textarea';

export type EventAction = 'submit' | 'delete' | 'approve' | 'reject' | 'cancel';

const COPY: Record<EventAction, { title: string; description: string; confirm: string }> = {
  submit: {
    title: 'Gửi duyệt',
    description: 'Sau khi gửi sẽ không sửa được nữa, trừ khi bị từ chối.',
    confirm: 'Gửi duyệt',
  },
  delete: {
    title: 'Xóa sự kiện',
    description: 'Bản nháp sẽ bị xóa vĩnh viễn.',
    confirm: 'Xóa',
  },
  approve: {
    title: 'Duyệt sự kiện',
    description: 'Sự kiện sẽ được công bố và mở cho người dùng xem, đặt vé.',
    confirm: 'Duyệt',
  },
  reject: {
    title: 'Từ chối sự kiện',
    description: 'Người tổ chức sẽ thấy lý do, sửa lại rồi gửi duyệt lần nữa.',
    confirm: 'Từ chối',
  },
  cancel: {
    title: 'Hủy sự kiện',
    description: 'Không hoàn tác được. Vé đã bán bị hủy, đơn đã thanh toán chuyển sang chờ hoàn tiền.',
    confirm: 'Hủy sự kiện',
  },
};

const DONE: Record<EventAction, (title: string) => string> = {
  submit: (title) => `Đã gửi duyệt "${title}"`,
  delete: (title) => `Đã xóa bản nháp "${title}"`,
  approve: (title) => `Đã duyệt và công bố "${title}"`,
  reject: (title) => `Đã từ chối "${title}"`,
  cancel: (title) => `Đã hủy sự kiện "${title}"`,
};

type EventActionDialogProps = {
  action: EventAction;
  event: EventItem;
  onClose: () => void;
  onDone: () => void;
};

export function EventActionDialog({ action, event, onClose, onDone }: EventActionDialogProps) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[action];

  async function submit() {
    setError(null);
    setSubmitting(true);
    try {
      if (action === 'submit') await submitEvent(event.id);
      else if (action === 'delete') await deleteEvent(event.id);
      else if (action === 'approve') await approveEvent(event.id);
      else if (action === 'reject') await rejectEvent(event.id, reason.trim());
      else await cancelEvent(event.id);
      toast.success(DONE[action](event.title));
      onDone();
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>
            <span className="font-medium text-foreground">{event.title}</span>. {copy.description}
          </DialogDescription>
        </DialogHeader>
        {action === 'reject' && (
          <div className="space-y-2">
            <Label htmlFor="reject-reason">Lý do từ chối</Label>
            <Textarea
              id="reject-reason"
              maxLength={500}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ví dụ: Thiếu ảnh bìa, mô tả chưa rõ thời gian check-in"
            />
          </div>
        )}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            Quay lại
          </Button>
          <Button
            variant={action === 'approve' || action === 'submit' ? 'default' : 'destructive'}
            onClick={() => void submit()}
            disabled={submitting || (action === 'reject' && !reason.trim())}
          >
            {submitting && <Spinner />}
            {copy.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
