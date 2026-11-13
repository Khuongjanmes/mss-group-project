/** Bộ lọc thời gian của trang sự kiện, theo `TIME_PRESETS` của openevent; mốc ngày, tuần, tháng tính theo giờ Việt Nam. */
export type TimePreset = 'today' | 'week' | 'month' | 'nextMonth' | 'next30' | 'last30';

export const TIME_PRESETS: { key: TimePreset; label: string }[] = [
  { key: 'today', label: 'Hôm nay' },
  { key: 'week', label: 'Tuần này' },
  { key: 'month', label: 'Tháng này' },
  { key: 'nextMonth', label: 'Tháng sau' },
  { key: 'next30', label: '30 ngày tới' },
  { key: 'last30', label: '30 ngày qua' },
];

const DAY_MS = 24 * 60 * 60 * 1000;
const dateParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' });

export function parseTimePreset(value: string | null): TimePreset | undefined {
  return TIME_PRESETS.some((preset) => preset.key === value) ? (value as TimePreset) : undefined;
}

/** 00:00 giờ Việt Nam của ngày chứa `now`; có `monthOffset` thì là ngày 1 của tháng đó cộng `monthOffset` tháng. */
function vietnamMidnight(now: Date, monthOffset?: number): Date {
  const ymd = dateParts.format(now); // 2026-11-12
  if (monthOffset === undefined) return new Date(`${ymd}T00:00:00+07:00`);
  const [year, month] = ymd.split('-').map(Number);
  const index = year * 12 + month - 1 + monthOffset;
  return new Date(`${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}-01T00:00:00+07:00`);
}

const addDays = (date: Date, days: number) => new Date(date.getTime() + days * DAY_MS);

/**
 * Đổi bộ lọc thành `from`, `to` của `GET /api/events`: API giữ sự kiện kết thúc sau `from` và bắt đầu trước `to`
 * (giao với khoảng), giống `filterEvents` của openevent.
 */
export function timeRange(key: TimePreset | undefined, now: Date = new Date()): { from?: string; to?: string } {
  if (!key) return {};
  const today = vietnamMidnight(now);
  let from: Date;
  let to: Date;
  switch (key) {
    case 'today':
      [from, to] = [today, addDays(today, 1)];
      break;
    case 'week': {
      // Tuần bắt đầu thứ Hai; +7h đưa 00:00 giờ Việt Nam về 00:00 UTC cùng ngày để đọc thứ.
      const weekday = new Date(today.getTime() + 7 * 60 * 60 * 1000).getUTCDay();
      from = addDays(today, -((weekday + 6) % 7));
      to = addDays(from, 7);
      break;
    }
    case 'month':
      [from, to] = [vietnamMidnight(now, 0), vietnamMidnight(now, 1)];
      break;
    case 'nextMonth':
      [from, to] = [vietnamMidnight(now, 1), vietnamMidnight(now, 2)];
      break;
    case 'next30':
      [from, to] = [now, addDays(now, 30)];
      break;
    case 'last30':
      [from, to] = [addDays(now, -30), now];
      break;
  }
  return { from: from.toISOString(), to: to.toISOString() };
}
