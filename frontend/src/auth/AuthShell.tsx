import { ArrowLeft, CalendarDays } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';

/** Vòng tròn mờ trên nền cam, theo LoginPage của openevent; từ `sm` có thêm vài vòng. */
function Decorations() {
  return (
    <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
      <div className="absolute -top-32 -right-24 size-80 rounded-full bg-primary-foreground/10 sm:size-112" />
      <div className="absolute -bottom-40 -left-24 size-96 rounded-full bg-primary-foreground/10 sm:size-128" />
      <div className="absolute top-1/3 -left-16 hidden size-48 rounded-full border-12 border-primary-foreground/10 sm:block" />
      <div className="absolute right-[12%] bottom-[18%] hidden size-24 rounded-full border-2 border-dashed border-primary-foreground/30 sm:block" />
    </div>
  );
}

/** Khung chung của đăng nhập, đăng ký, quên mật khẩu: nền cam, logo, thẻ trắng ở giữa (theo openevent). */
export function AuthShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    // Chữ trắng trên nền cam phải semibold trở lên (DESIGN.md §2).
    <div className="relative isolate flex min-h-svh flex-col overflow-hidden bg-primary px-4 py-5 text-primary-foreground sm:px-8 sm:py-6">
      <Decorations />

      <header className="flex items-center justify-between gap-4">
        <Link to="/events" className="flex items-center gap-2 font-bold" aria-label="Sự kiện">
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary-foreground text-primary sm:size-11">
            <CalendarDays className="size-5 sm:size-6" aria-hidden />
          </span>
          <span className="text-lg sm:text-xl">OpenEvent</span>
        </Link>
        <Button
          asChild
          variant="ghost"
          size="sm"
          className="font-semibold text-primary-foreground hover:bg-primary-foreground/15 hover:text-primary-foreground"
        >
          <Link to="/events">
            <ArrowLeft />
            Sự kiện
          </Link>
        </Button>
      </header>

      <main className="mx-auto flex w-full max-w-md flex-1 items-center py-10 sm:py-12">
        <section className="w-full space-y-7 rounded-3xl bg-card px-5 py-7 text-card-foreground shadow-2xl sm:px-8 sm:py-8">
          <div className="space-y-3 text-center">
            <h1 className="text-2xl font-bold tracking-tight md:text-[28px]">{title}</h1>
            <p className="text-sm text-muted-foreground">{description}</p>
          </div>
          {children}
        </section>
      </main>
    </div>
  );
}
