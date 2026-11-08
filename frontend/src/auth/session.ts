import { useSyncExternalStore } from 'react';

export type Role = 'USER' | 'ADMIN';

/** UserResponse của Identity. */
export interface SessionUser {
  id: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  roles: Role[];
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: SessionUser;
}

const STORAGE_KEY = 'openevent.session';
const listeners = new Set<() => void>();
let current: Session | null = read();

function read(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function getSession(): Session | null {
  return current;
}

export function setSession(session: Session | null) {
  current = session;
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Trình duyệt chặn localStorage: phiên chỉ sống trong tab này.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// Đăng nhập hoặc đăng xuất ở tab khác cũng cập nhật tab này.
window.addEventListener('storage', (event) => {
  if (event.key === STORAGE_KEY) {
    current = read();
    listeners.forEach((listener) => listener());
  }
});

export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession);
}

export function hasRole(session: Session | null, role: Role): boolean {
  return session?.user.roles.includes(role) ?? false;
}

/** Trang đầu tiên sau khi đăng nhập. */
export function homePathFor(session: Session): string {
  return hasRole(session, 'ADMIN') ? '/admin' : '/';
}

/** Chỉ chuyển tới đường dẫn nội bộ, tránh open redirect qua ?redirect=. */
export function safeRedirect(target: string | null): string | null {
  return target && target.startsWith('/') && !target.startsWith('//') && !target.startsWith('/\\')
    ? target
    : null;
}
