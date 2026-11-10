import { CheckCircle2, ScanLine, XCircle } from 'lucide-react';
import { type FormEvent, useRef, useState } from 'react';

import { errorMessage } from '@/api/client';
import { checkInTicket, type Ticket } from '@/api/tickets';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { formatDateTime } from '@/lib/datetime';

type Result = { ok: true; ticket: Ticket } | { ok: false; message: string };

/**
 * Check-in tại cửa (BR-09): máy quét QR dạng bàn phím gõ mã rồi Enter, hoặc nhập tay. Người quản lý sự kiện và
 * STAFF dùng được; Ticket hỏi Event để xác nhận quyền.
 */
export default function CheckInPage() {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<Result | null>(null);
  const input = useRef<HTMLInputElement>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const value = code.trim();
    if (!value) return;
    setBusy(true);
    try {
      setResult({ ok: true, ticket: await checkInTicket(value) });
    } catch (err) {
      setResult({ ok: false, message: errorMessage(err) });
    } finally {
      setBusy(false);
      setCode('');
      input.current?.focus();
    }
  }

  return (
    <div className="space-y-6 text-sm">
      <PageHeader
        title="Check-in"
        description="Quét mã QR trên vé (máy quét tự nhập mã) hoặc gõ mã rồi nhấn Enter. Mỗi vé chỉ check-in một lần, trong những ngày sự kiện diễn ra."
      />
      <form onSubmit={(e) => void submit(e)} className="flex max-w-xl gap-2">
        <div className="relative flex-1">
          <ScanLine className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            ref={input}
            autoFocus
            autoComplete="off"
            aria-label="Mã vé"
            placeholder="Mã vé"
            maxLength={64}
            className="h-11 pl-9 font-mono"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            disabled={busy}
          />
        </div>
        <Button type="submit" className="h-11" disabled={busy || !code.trim()}>
          {busy && <Spinner />}
          Check-in
        </Button>
      </form>

      {result && (
        <Card role="status" className={result.ok ? 'border-success/40' : 'border-destructive/40'}>
          <CardContent className="flex items-start gap-3">
            {result.ok ? (
              <>
                <CheckCircle2 className="size-8 shrink-0 text-success" aria-hidden />
                <div>
                  <p className="text-base font-semibold">Hợp lệ: {result.ticket.ticketTypeName ?? 'Vé'}</p>
                  <p className="text-muted-foreground">
                    Check-in lúc {result.ticket.checkedInAt ? formatDateTime(result.ticket.checkedInAt) : 'vừa xong'}
                  </p>
                </div>
              </>
            ) : (
              <>
                <XCircle className="size-8 shrink-0 text-destructive" aria-hidden />
                <p className="text-base font-semibold text-destructive">{result.message}</p>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

