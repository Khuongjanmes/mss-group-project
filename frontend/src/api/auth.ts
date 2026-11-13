import { getSession, setSession, type Session, type SessionUser } from '@/auth/session';
import { api } from './client';

interface AuthResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: SessionUser;
}

export async function login(email: string, password: string): Promise<Session> {
  const { data } = await api.post<AuthResponse>('/api/auth/login', { email, password });
  return { accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user };
}

/** Tạo tài khoản và đăng nhập luôn (server trả token như khi đăng nhập). */
export async function register(fullName: string, email: string, password: string): Promise<Session> {
  const { data } = await api.post<AuthResponse>('/api/auth/register', { fullName, email, password });
  return { accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user };
}

/** Thu hồi refresh token trên server. */
export async function logout(refreshToken: string): Promise<void> {
  await api.post('/api/auth/logout', { refreshToken });
}

/** Xóa phiên ở máy trước để giao diện đổi ngay, rồi thu hồi refresh token; lỗi mạng lúc này bỏ qua. */
export async function signOut(): Promise<void> {
  const session = getSession();
  setSession(null);
  if (session) {
    await logout(session.refreshToken).catch(() => undefined);
  }
}
