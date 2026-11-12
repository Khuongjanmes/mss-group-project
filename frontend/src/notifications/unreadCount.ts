import { useEffect, useSyncExternalStore } from 'react';

import { unreadNotificationCount } from '@/api/notifications';

/** Hỏi lại số chưa đọc định kỳ để chuông cập nhật khi có vé mới hay sự kiện bị hủy. */
const POLL_MS = 60_000;

// Một nguồn số chưa đọc cho cả chuông và trang Thông báo: đọc ở trang này thì chuông giảm ngay.
let count = 0;
const listeners = new Set<() => void>();

function emit(next: number) {
  count = next;
  listeners.forEach((listener) => listener());
}

export function setUnreadCount(next: number) {
  emit(Math.max(0, next));
}

export function decrementUnreadCount() {
  emit(Math.max(0, count - 1));
}

export async function refreshUnreadCount() {
  try {
    emit(await unreadNotificationCount());
  } catch {
    // Notification tạm lỗi: giữ số cũ, lần hỏi sau cập nhật lại.
  }
}

/** Số chưa đọc; hỏi lại định kỳ và khi người dùng quay lại tab. */
export function useUnreadCount(enabled: boolean): number {
  const value = useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => count,
  );

  useEffect(() => {
    if (!enabled) return;
    void refreshUnreadCount();
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refreshUnreadCount();
    }, POLL_MS);
    const onFocus = () => void refreshUnreadCount();
    window.addEventListener('focus', onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', onFocus);
    };
  }, [enabled]);

  return enabled ? value : 0;
}
