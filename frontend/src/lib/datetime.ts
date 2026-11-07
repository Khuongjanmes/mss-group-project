// Định dạng theo giờ Việt Nam dù máy người dùng ở múi giờ nào (theo openevent).
const TIME_ZONE = 'Asia/Ho_Chi_Minh';

const dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const weekdayFormatter = new Intl.DateTimeFormat('vi-VN', { timeZone: TIME_ZONE, weekday: 'long' });

const parts = (value: string) => {
  const list = dateTimeFormatter.formatToParts(new Date(value));
  const get = (type: Intl.DateTimeFormatPartTypes) => list.find((p) => p.type === type)?.value ?? '';
  return { day: get('day'), month: get('month'), year: get('year'), hour: get('hour'), minute: get('minute') };
};

/** `07/11/2026`. */
export function formatDate(iso: string): string {
  const p = parts(iso);
  return `${p.day}/${p.month}/${p.year}`;
}

/** `14:30`. */
export function formatTime(iso: string): string {
  const p = parts(iso);
  return `${p.hour}:${p.minute}`;
}

/** `07/11/2026 14:30`. */
export function formatDateTime(iso: string): string {
  return `${formatDate(iso)} ${formatTime(iso)}`;
}

/** `Thứ Bảy, 07/11/2026`. */
export function formatLongDate(iso: string): string {
  return `${weekdayFormatter.format(new Date(iso))}, ${formatDate(iso)}`;
}

/** Cùng ngày: `Thứ Bảy, 07/11/2026 · 10:00 – 12:00`; khác ngày: `07/11/2026 10:00 – 08/11/2026 12:00`. */
export function formatDateRange(start: string, end: string): string {
  if (formatDate(start) === formatDate(end)) {
    return `${formatLongDate(start)} · ${formatTime(start)} – ${formatTime(end)}`;
  }
  return `${formatDateTime(start)} – ${formatDateTime(end)}`;
}

const pad = (value: number) => String(value).padStart(2, '0');

/** ISO → giá trị cho `<input type="datetime-local">` theo giờ máy người dùng. */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Giá trị `datetime-local` (giờ máy người dùng) → ISO UTC để gửi API. */
export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}
