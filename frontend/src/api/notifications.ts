import { api } from './client'
import type { PageResponse } from './types'

export type NotificationType = 'TICKET_ISSUED' | 'EVENT_CANCELLED' | 'PASSWORD_RESET'

/** NotificationResponse của Notification Service. */
export interface AppNotification {
  id: string
  type: NotificationType
  title: string
  content: string
  /** EMAIL: đã gửi email; IN_APP: máy chủ chưa cấu hình mail, chỉ có trong ứng dụng. */
  channel: 'EMAIL' | 'IN_APP'
  read: boolean
  sentAt: string
}

export interface NotificationPage extends PageResponse<AppNotification> {
  unreadCount: number
}

/** Trang mở khi bấm vào thông báo. */
export const NOTIFICATION_TARGET: Record<NotificationType, string> = {
  TICKET_ISSUED: '/tickets',
  EVENT_CANCELLED: '/orders',
  PASSWORD_RESET: '/events',
}

/** Thông báo của người đang đăng nhập, mới nhất trước, kèm số chưa đọc. */
export async function myNotifications(page = 0, size = 20): Promise<NotificationPage> {
  return (await api.get<NotificationPage>('/api/notifications/my', { params: { page, size } })).data
}

export async function unreadNotificationCount(): Promise<number> {
  return (await api.get<{ unreadCount: number }>('/api/notifications/my/unread-count')).data.unreadCount
}

/** Gọi lại với thông báo đã đọc không lỗi. */
export async function markNotificationRead(id: string): Promise<void> {
  await api.patch(`/api/notifications/${id}/read`)
}
