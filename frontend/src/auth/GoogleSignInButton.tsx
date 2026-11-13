import { useEffect, useRef, useState } from 'react';

import { googleClientId, loginWithGoogle } from '@/api/auth';
import { errorMessage } from '@/api/client';
import type { Session } from '@/auth/session';

const GIS_SRC = 'https://accounts.google.com/gsi/client';
// Google vẽ nút trong iframe với chiều rộng cố định (200–400px).
const MIN_WIDTH = 200;
const MAX_WIDTH = 320;

type GoogleId = {
  initialize(options: { client_id: string; callback: (response: { credential?: string }) => void }): void;
  renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
};
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

let gisLoading: Promise<GoogleId> | undefined;

/** Tải script Google Identity Services một lần cho cả app. */
function loadGis(): Promise<GoogleId> {
  gisLoading ??= new Promise((resolve, reject) => {
    const script = Object.assign(document.createElement('script'), { src: GIS_SRC, async: true });
    script.onload = () => (window.google ? resolve(window.google.accounts.id) : reject(new Error('GIS')));
    script.onerror = () => {
      gisLoading = undefined;
      reject(new Error('GIS'));
    };
    document.head.appendChild(script);
  });
  return gisLoading;
}

/**
 * Nút "Đăng nhập với Google" theo GoogleSignInButton của openevent, gọi Google Identity Services trực tiếp (không thêm
 * thư viện). Client ID hỏi Identity lúc chạy; chưa cấu hình thì không hiện gì.
 */
export function GoogleSignInButton({ onSignedIn }: { onSignedIn: (session: Session) => void }) {
  const container = useRef<HTMLDivElement>(null);
  const [clientId, setClientId] = useState<string>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    googleClientId().then(setClientId).catch(() => setClientId(''));
  }, []);

  useEffect(() => {
    const element = container.current;
    if (!clientId || !element) return;
    loadGis()
      .then((google) => {
        google.initialize({
          client_id: clientId,
          callback: ({ credential }) => {
            if (!credential) return;
            setError(undefined);
            loginWithGoogle(credential).then(onSignedIn).catch((err: unknown) => setError(errorMessage(err)));
          },
        });
        const width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.floor(element.clientWidth)));
        google.renderButton(element, { theme: 'outline', size: 'large', shape: 'pill', text: 'signin_with', locale: 'vi', width });
      })
      .catch(() => setError('Không tải được nút Google, kiểm tra kết nối mạng.'));
  }, [clientId, onSignedIn]);

  if (!clientId) return null;
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 text-xs text-muted-foreground" aria-hidden>
        <span className="h-px flex-1 bg-border" />
        hoặc
        <span className="h-px flex-1 bg-border" />
      </div>
      <div ref={container} className="flex w-full justify-center" />
      {error && (
        <p role="alert" className="text-center text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
