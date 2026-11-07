import axios from 'axios'

import type { ApiError } from './types'

/** Axios dùng chung, gọi qua Gateway. */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
})

// shortcut: chỉ gửi access token đang có; refresh token khi gặp 401 (chỉ gọi refresh một lần) do TV1 thêm cùng màn hình đăng nhập.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Lấy thông báo lỗi để hiển thị cho người dùng. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error) && error.response?.data?.message) {
    return error.response.data.message
  }
  return 'Không kết nối được máy chủ'
}
