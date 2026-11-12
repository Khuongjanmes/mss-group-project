import type { BadgeVariant } from '@/components/ui/badge'

import { api } from './client'
import { startPayment } from './payments'
import type { PageResponse } from './types'

export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED'

export interface OrderItem {
  ticketTypeId: string
  quantity: number
  /** Đơn giá Ticket trả khi giữ vé; client không gửi giá. */
  unitPrice: number
}

/** OrderResponse của Order Service. */
export interface Order {
  id: string
  eventId: string
  status: OrderStatus
  totalAmount: number
  /** Hết hạn giữ vé (BR-02): quá thời điểm này đơn PENDING chuyển EXPIRED. */
  expiresAt: string
  createdAt: string
  items: OrderItem[]
}

/** BR-03: mỗi tài khoản tối đa 5 vé cho một sự kiện, tính cả các đơn trước. */
export const MAX_TICKETS_PER_EVENT = 5

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
}

export const ORDER_STATUS_BADGE: Record<OrderStatus, BadgeVariant> = {
  PENDING: 'warning',
  PAID: 'success',
  CANCELLED: 'neutral',
  EXPIRED: 'neutral',
}

/** Tạo đơn PENDING và giữ vé 10 phút (KB-1). */
export async function createOrder(
  eventId: string,
  items: { ticketTypeId: string; quantity: number }[],
): Promise<Order> {
  return (await api.post<Order>('/api/orders', { eventId, items })).data
}

/** Đơn của người đang đăng nhập, mới nhất trước. */
export async function myOrders(page = 0, size = 10): Promise<PageResponse<Order>> {
  return (await api.get<PageResponse<Order>>('/api/orders/my', { params: { page, size } })).data
}

/** Chỉ đơn PENDING của chính mình; Ticket nhả vé ngay. */
export async function cancelOrder(id: string): Promise<Order> {
  return (await api.put<Order>(`/api/orders/${id}/cancel`)).data
}

/** Đơn còn thanh toán được: PENDING và chưa quá hạn giữ vé. */
export function isPayable(order: Order, now = Date.now()): boolean {
  return order.status === 'PENDING' && Date.parse(order.expiresAt) > now
}

/** Mở giao dịch cho đơn rồi chuyển trình duyệt sang cổng thanh toán (payOS, VNPay hoặc cổng giả lập). */
export async function payOrder(orderId: string): Promise<void> {
  const { paymentUrl } = await startPayment(orderId)
  window.location.assign(paymentUrl)
}
