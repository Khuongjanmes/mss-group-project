import { api } from './client'
import type { PageResponse } from './types'

export type EventStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'

export interface Category {
  id: string
  name: string
  slug: string
  description: string | null
}

export interface Venue {
  id: string
  name: string
  address: string
  city: string
  capacity: number | null
}

export interface EventItem {
  id: string
  /** HOST của sự kiện. */
  organizerId: string
  /** `null` với sự kiện cá nhân. */
  organizationId: string | null
  organizationName: string | null
  title: string
  description: string | null
  bannerUrl: string | null
  category: Category
  venue: Venue
  startTime: string
  endTime: string
  status: EventStatus
  rejectReason: string | null
  createdAt: string
}

export interface EventSearchParams {
  keyword?: string
  category?: string
  city?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function searchEvents(params: EventSearchParams = {}): Promise<PageResponse<EventItem>> {
  const response = await api.get<PageResponse<EventItem>>('/api/events', { params })
  return response.data
}

export interface AdminEventSearchParams {
  keyword?: string
  status?: EventStatus
  page?: number
  size?: number
}

/** ADMIN: mọi trạng thái, mới nhất trước. */
export async function searchAdminEvents(params: AdminEventSearchParams): Promise<PageResponse<EventItem>> {
  const response = await api.get<PageResponse<EventItem>>('/api/events/admin', { params })
  return response.data
}

export async function approveEvent(id: string): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${id}/approve`)).data
}

export async function rejectEvent(id: string, reason: string): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${id}/reject`, { reason })).data
}

export async function cancelEvent(id: string): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${id}/cancel`)).data
}

/** Body tạo và sửa sự kiện; thời gian dạng ISO-8601. */
export interface EventRequest {
  title: string
  description: string | null
  bannerUrl: string | null
  categoryId: string
  venueId: string
  startTime: string
  endTime: string
  /** Chỉ dùng lúc tạo; sửa thì server bỏ qua (BR-14). */
  organizationId?: string | null
}

/** Sự kiện mình làm HOST, mọi trạng thái. */
export async function searchMyEvents(params: { status?: EventStatus; page?: number; size?: number }) {
  return (await api.get<PageResponse<EventItem>>('/api/events/my', { params })).data
}

/** Sự kiện mình soát vé được (HOST, STAFF, OWNER/MANAGER), đã công bố hoặc vừa kết thúc, sớm nhất trước. */
export async function listCheckInEvents(): Promise<EventItem[]> {
  return (await api.get<EventItem[]>('/api/events/check-in')).data
}

export async function getEvent(id: string): Promise<EventItem> {
  return (await api.get<EventItem>(`/api/events/${id}`)).data
}

export async function createEvent(body: EventRequest): Promise<EventItem> {
  return (await api.post<EventItem>('/api/events', body)).data
}

export async function updateEvent(id: string, body: EventRequest): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${id}`, body)).data
}

export async function deleteEvent(id: string): Promise<void> {
  await api.delete(`/api/events/${id}`)
}

export async function submitEvent(id: string): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${id}/submit`)).data
}

export async function listCategories(): Promise<Category[]> {
  return (await api.get<Category[]>('/api/categories')).data
}

// shortcut: lấy tối đa 100 địa điểm cho ô chọn; nhiều hơn thì đổi sang ô tìm kiếm.
export async function listVenues(): Promise<Venue[]> {
  return (await api.get<PageResponse<Venue>>('/api/venues', { params: { size: 100 } })).data.content
}

/** STAFF của sự kiện: chỉ xem và check-in (BR-09). `email`, `fullName` là `null` khi Identity tạm lỗi. */
export interface Staff {
  userId: string
  email: string | null
  fullName: string | null
}

export async function listStaff(eventId: string): Promise<Staff[]> {
  return (await api.get<Staff[]>(`/api/events/${eventId}/staff`)).data
}

/** Thêm bằng email tài khoản OpenEvent; không cần thuộc tổ chức. */
export async function addStaff(eventId: string, email: string): Promise<Staff> {
  return (await api.post<Staff>(`/api/events/${eventId}/staff`, { email })).data
}

export async function removeStaff(eventId: string, userId: string): Promise<void> {
  await api.delete(`/api/events/${eventId}/staff/${userId}`)
}

/** Đổi HOST sự kiện thuộc tổ chức; HOST mới phải là thành viên tổ chức (BR-14). */
export async function assignHost(eventId: string, userId: string): Promise<EventItem> {
  return (await api.put<EventItem>(`/api/events/${eventId}/host`, { userId })).data
}

/** Ảnh bìa đã tải lên Cloudinary; đưa `url` vào bannerUrl rồi lưu sự kiện. */
export interface UploadedImage {
  url: string
  publicId: string
}

/** JPG, PNG hoặc WEBP tối đa 5 MB; chỉ khi sự kiện còn sửa được. */
export async function uploadEventImage(eventId: string, file: File): Promise<UploadedImage> {
  const body = new FormData()
  body.append('file', file)
  return (await api.post<UploadedImage>(`/api/events/${eventId}/images`, body)).data
}

export interface CategoryRequest {
  name: string
  description: string | null
}

/** ADMIN; slug tự sinh từ tên, trùng tên thì lỗi CATEGORY_EXISTS. */
export async function createCategory(body: CategoryRequest): Promise<Category> {
  return (await api.post<Category>('/api/categories', body)).data
}

export async function updateCategory(id: string, body: CategoryRequest): Promise<Category> {
  return (await api.put<Category>(`/api/categories/${id}`, body)).data
}

/** Danh mục đang có sự kiện dùng thì lỗi CATEGORY_IN_USE. */
export async function deleteCategory(id: string): Promise<void> {
  await api.delete(`/api/categories/${id}`)
}

export interface VenueRequest {
  name: string
  address: string
  city: string
  capacity: number | null
}

export async function searchVenues(params: { city?: string; page?: number; size?: number }) {
  return (await api.get<PageResponse<Venue>>('/api/venues', { params })).data
}

/** Chỉ ADMIN thêm, sửa, xóa. */
export async function createVenue(body: VenueRequest): Promise<Venue> {
  return (await api.post<Venue>('/api/venues', body)).data
}

export async function updateVenue(id: string, body: VenueRequest): Promise<Venue> {
  return (await api.put<Venue>(`/api/venues/${id}`, body)).data
}

/** Địa điểm đang có sự kiện dùng thì lỗi VENUE_IN_USE. */
export async function deleteVenue(id: string): Promise<void> {
  await api.delete(`/api/venues/${id}`)
}
