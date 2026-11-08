import { useRef } from 'react';
import { useBeforeUnload, useBlocker } from 'react-router';

/**
 * Hỏi lại trước khi rời form còn thay đổi chưa lưu: chuyển trang trong app (blocker điều khiển hộp thoại xác nhận)
 * và đóng hoặc tải lại tab (hộp thoại của trình duyệt).
 * Gọi `allowNextNavigation()` ngay trước khi tự chuyển trang sau khi lưu xong.
 */
export function useUnsavedChangesGuard(dirty: boolean) {
  const allowed = useRef(false);
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (allowed.current) {
      allowed.current = false; // chỉ bỏ qua đúng một lần chuyển trang
      return false;
    }
    return dirty && currentLocation.pathname !== nextLocation.pathname;
  });
  useBeforeUnload((event) => {
    if (dirty) event.preventDefault();
  });
  return {
    blocker,
    allowNextNavigation: () => {
      allowed.current = true;
    },
  };
}
