import { CheckCircle2, ScanLine, XCircle } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';
import type { ScanOutcome } from './useScanCheckIn';

/** Tiêu đề theo mã lỗi của Ticket; nội dung chi tiết lấy nguyên thông báo của server. */
const ERROR_TITLES: Record<string, string> = {
  ALREADY_CHECKED_IN: 'Vé đã được dùng',
  TICKET_NOT_IN_EVENT: 'Vé của sự kiện khác',
  TICKET_NOT_FOUND: 'Mã vé không hợp lệ',
  TICKET_CANCELLED: 'Vé đã bị hủy',
  OUTSIDE_EVENT_DAYS: 'Không trong ngày diễn ra',
  NOT_EVENT_MANAGER: 'Bạn không có quyền soát vé',
};

/**
 * Kết quả một lần quét, đọc được từ xa: dải xanh (hợp lệ) hoặc đỏ (từ chối), rồi họ tên, email người mua để
 * đối chiếu. Giữ nguyên tới khi bấm "Quét tiếp".
 */
export function ScanResultCard({ outcome, onNext }: { outcome: ScanOutcome; onNext: () => void }) {
  const ok = outcome.kind === 'ok';
  const Icon = ok ? CheckCircle2 : XCircle;
  const title = ok ? 'Check-in thành công' : ((outcome.code && ERROR_TITLES[outcome.code]) ?? 'Không check-in được');

  return (
    <section aria-label="Kết quả quét" className="overflow-hidden rounded-3xl border bg-card shadow-sm">
      <div
        role="status"
        className={cn(
          'flex flex-col items-center gap-2 px-4 py-6 text-center',
          ok ? 'bg-success/12 text-success' : 'bg-destructive/10 text-destructive',
        )}
      >
        <Icon className="size-14" aria-hidden />
        <p className="text-xl font-bold">{title}</p>
        {outcome.kind === 'error' && <p className="text-sm text-foreground/80">{outcome.message}</p>}
      </div>

      {ok && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 p-4 text-sm">
          <dt className="text-muted-foreground">Người mua</dt>
          <dd className="truncate text-base font-semibold">{outcome.result.ownerName ?? 'Không rõ'}</dd>
          <dt className="text-muted-foreground">Email</dt>
          <dd className="truncate">{outcome.result.ownerEmail ?? '—'}</dd>
          <dt className="text-muted-foreground">Loại vé</dt>
          <dd className="font-medium">{outcome.result.ticketTypeName ?? 'Vé'}</dd>
          <dt className="text-muted-foreground">Lúc</dt>
          <dd className="tabular-nums">{formatDateTime(outcome.result.checkedInAt)}</dd>
        </dl>
      )}

      <div className="border-t p-4">
        <Button size="lg" className="h-12 w-full rounded-full text-base" onClick={onNext} autoFocus>
          <ScanLine />
          Quét tiếp
        </Button>
      </div>
    </section>
  );
}
