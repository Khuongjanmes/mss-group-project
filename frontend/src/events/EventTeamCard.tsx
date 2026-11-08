import { Crown, UserPlus, Users, X } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { addStaff, assignHost, type EventItem, listStaff, removeStaff, type Staff } from '@/api/events';
import { listMembers, type Member, ORGANIZATION_ROLE_LABELS } from '@/api/organizations';
import { useSession } from '@/auth/session';
import { UserAvatar } from '@/components/common/user';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';

const TITLE = 'flex items-center gap-2 text-[11px] font-bold tracking-wider text-foreground uppercase';

type EventTeamCardProps = {
  event: EventItem;
  /** HOST đổi xong thì tải lại sự kiện: người vừa giao lại có thể mất quyền quản lý. */
  onHostChanged: () => void;
};

/** Người phụ trách (HOST) và nhân sự hỗ trợ (STAFF) của sự kiện; chỉ người quản lý sự kiện thấy. */
export function EventTeamCard({ event, onHostChanged }: EventTeamCardProps) {
  return (
    <Card className="gap-0 rounded-xl py-0 shadow-sm">
      <CardContent className="grid gap-8 p-5 md:grid-cols-2">
        <HostSection event={event} onHostChanged={onHostChanged} />
        <StaffSection eventId={event.id} />
      </CardContent>
    </Card>
  );
}

function HostSection({ event, onHostChanged }: EventTeamCardProps) {
  const session = useSession();
  const [members, setMembers] = useState<Member[] | null>(null);
  const [hostId, setHostId] = useState(event.organizerId);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setHostId(event.organizerId);
    if (!event.organizationId) return;
    let ignore = false;
    listMembers(event.organizationId)
      .then((data) => !ignore && setMembers(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [event.organizationId, event.organizerId]);

  async function save() {
    setSaving(true);
    setError(null);
    try {
      await assignHost(event.id, hostId);
      toast.success('Đã đổi người phụ trách');
      onHostChanged();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="space-y-3">
      <h2 className={TITLE}>
        <Crown className="size-4 text-accent-foreground" aria-hidden />
        Người phụ trách (HOST)
      </h2>
      {!event.organizationId ? (
        <p className="text-sm text-muted-foreground">
          {event.organizerId === session?.user.id ? 'Bạn' : 'Người tạo'} phụ trách sự kiện này. Sự kiện cá nhân không
          đổi được người phụ trách.
        </p>
      ) : !members ? (
        error ? (
          <p className="text-sm text-destructive">{error}</p>
        ) : (
          <p className="text-sm text-muted-foreground">Đang tải thành viên…</p>
        )
      ) : (
        <>
          <p className="text-xs text-muted-foreground">
            Chọn một thành viên của {event.organizationName ?? 'tổ chức'}. Người phụ trách sửa, gửi duyệt và hủy được sự
            kiện.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Select value={hostId} onValueChange={setHostId} disabled={saving}>
              <SelectTrigger className="h-10 w-full bg-background" aria-label="Người phụ trách">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {!members.some((member) => member.userId === event.organizerId) && (
                  <SelectItem value={event.organizerId}>Người phụ trách hiện tại (đã rời tổ chức)</SelectItem>
                )}
                {members.map((member) => (
                  <SelectItem key={member.userId} value={member.userId}>
                    {member.fullName ?? member.email ?? 'Người dùng'} · {ORGANIZATION_ROLE_LABELS[member.role]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              className="h-10 shrink-0"
              disabled={saving || hostId === event.organizerId}
              onClick={() => void save()}
            >
              {saving && <Spinner />}
              Đổi người phụ trách
            </Button>
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
        </>
      )}
    </section>
  );
}

function StaffSection({ eventId }: { eventId: string }) {
  const [staff, setStaff] = useState<Staff[] | null>(null);
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    listStaff(eventId)
      .then((data) => !ignore && setStaff(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [eventId, reloadKey]);

  async function act(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      toast.success(success);
      setReloadKey((k) => k + 1);
      return true;
    } catch (err) {
      setError(errorMessage(err));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function add(e: FormEvent) {
    e.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Nhập email hợp lệ');
      return;
    }
    if (await act(() => addStaff(eventId, email.trim()), `Đã thêm ${email.trim()} làm STAFF`)) setEmail('');
  }

  const name = (person: Staff) => person.fullName ?? person.email ?? 'Người dùng';

  return (
    <section className="space-y-3">
      <h2 className={TITLE}>
        <Users className="size-4 text-accent-foreground" aria-hidden />
        Nhân sự hỗ trợ (STAFF)
      </h2>
      <p className="text-xs text-muted-foreground">
        STAFF chỉ xem sự kiện và check-in vé, không sửa được. Có thể là bất kỳ tài khoản OpenEvent nào.
      </p>
      <form onSubmit={(e) => void add(e)} noValidate className="flex flex-col gap-2 sm:flex-row">
        <Input
          type="email"
          autoComplete="off"
          aria-label="Email của STAFF"
          placeholder="Email tài khoản OpenEvent"
          maxLength={254}
          className="h-10 bg-background"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={busy}
        />
        <Button type="submit" variant="outline" className="h-10 shrink-0" disabled={busy || !email.trim()}>
          {busy ? <Spinner /> : <UserPlus />}
          Thêm
        </Button>
      </form>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {!staff ? (
        !error && <p className="text-sm text-muted-foreground">Đang tải…</p>
      ) : staff.length === 0 ? (
        <p className="rounded-lg border border-dashed p-3 text-center text-sm text-muted-foreground">Chưa có STAFF nào.</p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {staff.map((person) => (
            <li key={person.userId} className="flex items-center gap-3 px-3 py-2">
              <UserAvatar user={{ name: name(person), email: person.email ?? '' }} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{name(person)}</p>
                {person.email && <p className="truncate text-xs text-muted-foreground">{person.email}</p>}
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="size-8 rounded-full text-muted-foreground hover:text-destructive"
                aria-label={`Xóa ${name(person)} khỏi STAFF`}
                disabled={busy}
                onClick={() => void act(() => removeStaff(eventId, person.userId), `Đã xóa ${name(person)} khỏi STAFF`)}
              >
                <X className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
