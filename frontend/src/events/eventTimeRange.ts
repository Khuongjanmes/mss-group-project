/** Khoảng thời gian lọc nhanh ở trang tìm sự kiện, theo bộ lọc "Từ ngày / Đến ngày" của openevent SBA301. */
export type TimeRangeKey = 'today' | 'weekend' | 'week' | 'month';

export const TIME_RANGES: { key: TimeRangeKey; label: string }[] = [
  { key: 'today', label: 'Hôm nay' },
  { key: 'weekend', label: 'Cuối tuần này' },
  { key: 'week', label: '7 ngày tới' },
  { key: 'month', label: '30 ngày tới' },
];

const DAY_MS = 24 * 60 * 60 * 1000;

export function parseTimeRange(value: string | null): TimeRangeKey | undefined {
  return TIME_RANGES.some((range) => range.key === value) ? (value as TimeRangeKey) : undefined;
}

function startOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/**
 * Đổi khoảng lọc thành `from`, `to` của `GET /api/events`: API giữ sự kiện kết thúc sau `from` và bắt đầu trước `to`,
 * nên `from = now` cho ra cả sự kiện đang diễn ra. Tính theo giờ máy người dùng.
 */
export function timeRange(key: TimeRangeKey | undefined, now: Date = new Date()): { from?: string; to?: string } {
  if (!key) return {};
  const today = startOfDay(now);
  let to: Date;
  switch (key) {
    case 'today':
      to = new Date(today.getTime() + DAY_MS);
      break;
    case 'week':
      to = new Date(now.getTime() + 7 * DAY_MS);
      break;
    case 'month':
      to = new Date(now.getTime() + 30 * DAY_MS);
      break;
    case 'weekend': {
      // getDay(): 0 = Chủ nhật, 6 = Thứ Bảy. Cuối tuần là từ 00:00 Thứ Bảy đến 00:00 Thứ Hai.
      const daysToSaturday = (6 - today.getDay() + 7) % 7;
      const saturday = today.getDay() === 0 ? new Date(today.getTime() - DAY_MS) : new Date(today.getTime() + daysToSaturday * DAY_MS);
      to = new Date(saturday.getTime() + 2 * DAY_MS);
      const from = saturday > now ? saturday : now;
      return { from: from.toISOString(), to: to.toISOString() };
    }
  }
  return { from: now.toISOString(), to: to.toISOString() };
}
