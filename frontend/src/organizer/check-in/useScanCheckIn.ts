import { useRef, useState } from 'react';

import { errorCode, errorMessage } from '@/api/client';
import { checkInTicket, type CheckInResult } from '@/api/tickets';

/** Mã QR thường còn trước camera sau khi có kết quả: bỏ qua đúng mã đó trong chừng này sau khi quét tiếp. */
const SAME_CODE_MS = 6000;

export type ScanOutcome =
  | { kind: 'ok'; result: CheckInResult }
  | { kind: 'error'; code?: string; message: string };

/**
 * Quét → check-in ngay → giữ kết quả tới khi bấm "Quét tiếp" → bật lại camera (theo openevent). Điện thoại rung
 * ngắn khi hợp lệ, rung dài khi bị từ chối.
 */
export function useScanCheckIn(eventId: string, onCheckedIn: () => void) {
  const [outcome, setOutcome] = useState<ScanOutcome>();
  const [pending, setPending] = useState(false);
  const last = useRef<{ code: string; at: number }>(undefined);

  /** `fromCamera`: chỉ mã từ camera mới bị bỏ qua khi lặp; mã gõ tay là người soát chủ động nhập lại. */
  async function submit(value: string, fromCamera: boolean) {
    const code = value.trim();
    if (!code || pending || outcome) return;
    const now = Date.now();
    if (fromCamera && last.current?.code === code && now - last.current.at < SAME_CODE_MS) return;
    last.current = { code, at: now };
    setPending(true);
    let next: ScanOutcome;
    try {
      next = { kind: 'ok', result: await checkInTicket(eventId, code) };
      onCheckedIn();
    } catch (err) {
      next = { kind: 'error', code: errorCode(err), message: errorMessage(err) };
    } finally {
      setPending(false);
    }
    navigator.vibrate?.(next.kind === 'ok' ? 80 : [60, 60, 200]);
    setOutcome(next);
  }

  return {
    submit: (value: string, fromCamera = false) => void submit(value, fromCamera),
    outcome,
    busy: pending || Boolean(outcome),
    /** Đóng kết quả, quét người tiếp theo; khoảng bỏ qua mã cũ tính từ lúc camera bật lại. */
    next: () => {
      if (last.current) last.current.at = Date.now();
      setOutcome(undefined);
    },
  };
}
