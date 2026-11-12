import axios from 'axios';
import { toast } from 'sonner';

import { getSession, setSession } from '@/auth/session';
import type { ApiError } from './types';

/** Axios dùng chung, gọi qua Gateway. */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

api.interceptors.request.use((config) => {
  const token = getSession()?.accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// shortcut: access token hết hạn (15 phút) thì đăng xuất luôn. TV1 thay bằng refresh token,
// chỉ gọi refresh một lần khi nhiều request cùng gặp 401.
api.interceptors.response.use(undefined, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401 && getSession()) {
    setSession(null);
    toast.warning('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');
  }
  return Promise.reject(error);
});

/** Lấy thông báo lỗi để hiển thị cho người dùng. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error) && error.response?.data?.message) {
    return error.response.data.message;
  }
  return 'Không kết nối được máy chủ';
}

/** Mã lỗi nghiệp vụ (`SOLD_OUT`, `ALREADY_CHECKED_IN`...), không có khi không tới được máy chủ. */
export function errorCode(error: unknown): string | undefined {
  return axios.isAxiosError<ApiError>(error) ? error.response?.data?.code : undefined;
}

/** Lỗi theo từng trường khi server trả VALIDATION_FAILED. */
export function fieldErrors(error: unknown): Record<string, string> {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.fieldErrors ?? {}) : {};
}
