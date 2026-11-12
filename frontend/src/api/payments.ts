import type { BadgeVariant } from '@/components/ui/badge'

import { api } from './client'
import type { PageResponse } from './types'

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUND_PENDING' | 'REFUNDED'
export type PaymentMethod = 'PAYOS' | 'VNPAY' | 'MOCK'

export interface Payment {
  id: string
  orderId: string
  userId: string
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  transactionRef: string
  paidAt: string | null
  refundReason: string | null
  createdAt: string
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Chờ thanh toán',
  SUCCESS: 'Thành công',
  FAILED: 'Thất bại',
  REFUND_PENDING: 'Chờ hoàn tiền',
  REFUNDED: 'Đã hoàn tiền',
}

export const PAYMENT_STATUS_BADGE: Record<PaymentStatus, BadgeVariant> = {
  PENDING: 'neutral',
  SUCCESS: 'success',
  FAILED: 'destructive',
  REFUND_PENDING: 'warning',
  REFUNDED: 'info',
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PAYOS: 'payOS (VietQR)',
  VNPAY: 'VNPay',
  MOCK: 'Cổng giả lập',
}

const VND = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

export function formatMoney(amount: number): string {
  return VND.format(amount)
}

/** Trả URL cổng thanh toán; SPA chuyển trình duyệt sang đó. Số tiền do server lấy từ đơn. */
export async function startPayment(orderId: string): Promise<{ payment: Payment; paymentUrl: string }> {
  return (await api.post('/api/payments', { orderId })).data
}

export async function getPayment(id: string): Promise<Payment> {
  return (await api.get<Payment>(`/api/payments/${id}`)).data
}

/** Cổng giả lập: người trả tiền chọn kết quả. */
export async function completeMockPayment(id: string, success: boolean): Promise<Payment> {
  return (await api.post<Payment>(`/api/payments/${id}/mock-result`, { success })).data
}

/** Admin: mới nhất trước. */
export async function searchPayments(params: { status?: PaymentStatus; page?: number; size?: number }) {
  return (await api.get<PageResponse<Payment>>('/api/payments', { params })).data
}

export async function markPaymentRefunded(id: string): Promise<Payment> {
  return (await api.put<Payment>(`/api/payments/${id}/refunded`)).data
}
