/** Kiểu dữ liệu chung của mọi service (common-lib). Kiểu riêng của từng service đặt trong file của service đó. */

/** Lỗi API, xem ApiError trong common-lib. */
export interface ApiError {
  status: number
  code: string
  message: string
  path?: string
  fieldErrors?: Record<string, string>
}

/** Kết quả phân trang, xem PageResponse trong common-lib. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
