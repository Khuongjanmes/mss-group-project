import type { Attendee } from '@/api/tickets';

const STATUS: Record<Attendee['status'], string> = {
  ISSUED: 'Chưa check-in',
  CHECKED_IN: 'Đã check-in',
  CANCELLED: 'Đã hủy',
};

/** Ô CSV theo RFC 4180; ô bắt đầu bằng = + - @ thêm ' để Excel không chạy như công thức. */
export function csvCell(value: string | null | undefined): string {
  let text = value ?? '';
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/**
 * Danh sách người tham dự dạng CSV mở được bằng Excel: BOM UTF-8 để giữ tiếng Việt, xuống dòng CRLF.
 * `formatTime` truyền vào để hàm không phụ thuộc múi giờ máy chạy.
 */
export function attendeesCsv(attendees: Attendee[], formatTime: (iso: string) => string): string {
  const header = ['Họ tên', 'Email', 'Loại vé', 'Mã vé', 'Trạng thái', 'Check-in lúc'];
  const rows = attendees.map((a) => [
    a.ownerName,
    a.ownerEmail,
    a.ticketTypeName,
    a.code,
    STATUS[a.status],
    a.checkedInAt ? formatTime(a.checkedInAt) : '',
  ]);
  return '﻿' + [header, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n') + '\r\n';
}
