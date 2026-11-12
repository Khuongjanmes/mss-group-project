import { type IScannerError, Scanner } from '@yudiel/react-qr-scanner';
import { CameraOff, Keyboard } from 'lucide-react';
import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { type EventItem, getEvent } from '@/api/events';
import { ticketStats } from '@/api/tickets';
import { ErrorState, LoadingState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { formatDateRange } from '@/lib/datetime';
import { ScanResultCard } from './ScanResultCard';
import { useScanCheckIn } from './useScanCheckIn';

const CAMERA_ERRORS: Partial<Record<IScannerError['kind'], string>> = {
  'permission-denied': 'Bạn chưa cho phép dùng camera. Hãy bật quyền camera cho trang này rồi tải lại.',
  'no-camera': 'Không tìm thấy camera trên thiết bị.',
  'in-use': 'Camera đang được ứng dụng khác sử dụng.',
  'insecure-context': 'Trình duyệt chỉ cho dùng camera trên HTTPS hoặc localhost.',
  unsupported: 'Trình duyệt không hỗ trợ quét QR.',
};

/**
 * Soát vé một sự kiện (KB-3, BR-09), theo openevent: quét QR bằng camera sau, kết quả hiện ngay tới khi bấm
 * "Quét tiếp". Ô nhập mã luôn có sẵn cho vé in giấy, máy quét dạng bàn phím hoặc khi không có camera.
 */
export default function CheckInScanPage() {
  const { eventId = '' } = useParams();
  const [event, setEvent] = useState<EventItem>();
  const [error, setError] = useState<string>();
  const [count, setCount] = useState<{ checkedIn: number; sold: number }>();
  const [cameraError, setCameraError] = useState<string>();

  // Chỉ người quản lý xem được số liệu; STAFF nhận 403 thì ẩn dòng đếm.
  const refreshCount = useCallback(() => {
    ticketStats(eventId)
      .then((stats) => setCount({
        checkedIn: stats.reduce((n, s) => n + s.checkedIn, 0),
        sold: stats.reduce((n, s) => n + s.soldQuantity, 0),
      }))
      .catch(() => setCount(undefined));
  }, [eventId]);

  useEffect(() => {
    getEvent(eventId).then(setEvent).catch((err: unknown) => setError(errorMessage(err)));
    refreshCount();
  }, [eventId, refreshCount]);

  const scan = useScanCheckIn(eventId, refreshCount);

  if (error) return <ErrorState description={error} />;
  if (!event) return <LoadingState lines={4} />;

  return (
    <div className="mx-auto w-full max-w-md space-y-4">
      <header className="space-y-0.5">
        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Check-in</p>
        <h1 className="line-clamp-2 text-lg leading-tight font-semibold md:text-xl">{event.title}</h1>
        <p className="text-sm text-muted-foreground">{formatDateRange(event.startTime, event.endTime)}</p>
      </header>

      {count && (
        <p className="text-center text-sm text-muted-foreground tabular-nums" aria-live="polite">
          <span className="font-semibold text-foreground">{count.checkedIn}</span>/{count.sold} vé đã check-in
        </p>
      )}

      {scan.outcome ? (
        <ScanResultCard outcome={scan.outcome} onNext={scan.next} />
      ) : (
        <div className="space-y-2">
          <div className="relative aspect-square w-full overflow-hidden rounded-3xl bg-muted">
            {cameraError ? (
              <div role="alert" className="flex size-full flex-col items-center justify-center gap-3 p-6 text-center text-sm">
                <CameraOff className="size-8 text-muted-foreground" aria-hidden />
                <p>{cameraError}</p>
                <Button variant="outline" onClick={() => setCameraError(undefined)}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <Scanner
                onScan={(codes) => {
                  const value = codes[0]?.rawValue;
                  if (value) scan.submit(value, true);
                }}
                onError={(err) => setCameraError(CAMERA_ERRORS[(err as IScannerError).kind] ?? 'Không mở được camera.')}
                formats={['qr_code']}
                constraints={{ facingMode: 'environment' }}
                paused={scan.busy}
                sound={false}
                classNames={{ container: 'size-full', video: 'size-full object-cover' }}
              />
            )}
          </div>
          <p className="text-center text-sm text-muted-foreground">
            {scan.busy ? 'Đang check-in…' : 'Đưa mã QR trên vé vào khung hình.'}
          </p>
          <ManualCodeForm disabled={scan.busy} onSubmit={scan.submit} />
        </div>
      )}
    </div>
  );
}

/** Nhập mã tay: vé in giấy, máy quét dạng bàn phím (gõ mã rồi Enter), hoặc khi không có camera. */
function ManualCodeForm({ disabled, onSubmit }: { disabled: boolean; onSubmit: (value: string) => void }) {
  const [value, setValue] = useState('');

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit(value);
    setValue('');
  }

  return (
    <form onSubmit={submit} className="flex gap-2 pt-2">
      <div className="relative flex-1">
        <Keyboard className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          aria-label="Mã vé"
          placeholder="Hoặc nhập mã vé"
          autoComplete="off"
          maxLength={64}
          className="h-11 pl-9 font-mono"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </div>
      <Button type="submit" variant="outline" className="h-11" disabled={disabled || !value.trim()}>
        Check-in
      </Button>
    </form>
  );
}
