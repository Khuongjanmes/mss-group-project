import type { EventItem, EventRequest } from '@/api/events';
import { fromLocalInput, toLocalInput } from '@/lib/datetime';

/** Giá trị đang nhập; thời gian ở dạng `datetime-local`. */
export type EventForm = {
  title: string;
  description: string;
  bannerUrl: string;
  categoryId: string;
  venueId: string;
  startTime: string;
  endTime: string;
};

export type EventFieldErrors = Partial<Record<keyof EventForm, string>>;

export const TITLE_MAX_LENGTH = 200;

export const EMPTY_FORM: EventForm = {
  title: '',
  description: '',
  bannerUrl: '',
  categoryId: '',
  venueId: '',
  startTime: '',
  endTime: '',
};

export function toForm(event: EventItem): EventForm {
  return {
    title: event.title,
    description: event.description ?? '',
    bannerUrl: event.bannerUrl ?? '',
    categoryId: event.category.id,
    venueId: event.venue.id,
    startTime: toLocalInput(event.startTime),
    endTime: toLocalInput(event.endTime),
  };
}

export function toRequest(form: EventForm): EventRequest {
  return {
    title: form.title.trim(),
    description: form.description.trim() || null,
    bannerUrl: form.bannerUrl.trim() || null,
    categoryId: form.categoryId,
    venueId: form.venueId,
    startTime: fromLocalInput(form.startTime),
    endTime: fromLocalInput(form.endTime),
  };
}

export function isSameForm(a: EventForm, b: EventForm): boolean {
  return (Object.keys(a) as (keyof EventForm)[]).every((key) => a[key] === b[key]);
}

/** Báo lỗi ngay trên form; server vẫn kiểm tra lại (BR-07). */
export function validateForm(form: EventForm, now = new Date()): EventFieldErrors {
  const errors: EventFieldErrors = {};
  if (!form.title.trim()) errors.title = 'Nhập tên sự kiện';
  if (!form.categoryId) errors.categoryId = 'Chọn danh mục';
  if (!form.venueId) errors.venueId = 'Chọn địa điểm';
  if (!form.startTime) errors.startTime = 'Chọn thời gian bắt đầu';
  else if (new Date(form.startTime) <= now) errors.startTime = 'Thời gian bắt đầu phải ở tương lai';
  if (!form.endTime) errors.endTime = 'Chọn thời gian kết thúc';
  else if (form.startTime && new Date(form.endTime) <= new Date(form.startTime)) {
    errors.endTime = 'Thời gian kết thúc phải sau thời gian bắt đầu';
  }
  if (form.description.length > 20000) errors.description = 'Mô tả quá dài (tối đa 20.000 ký tự kể cả định dạng)';
  if (form.bannerUrl.trim() && !/^https?:\/\/\S+$/i.test(form.bannerUrl.trim())) {
    errors.bannerUrl = 'Phải là đường dẫn http hoặc https';
  }
  return errors;
}

/** `datetime-local` cộng thêm số giờ, giữ định dạng `datetime-local`. */
export function addHours(local: string, hours: number): string {
  return toLocalInput(new Date(new Date(local).getTime() + hours * 3_600_000).toISOString());
}
