import { Crown, KeyRound, LogOut, MoreVertical, Shield, ShieldOff, Trash2, UserPlus, Users } from 'lucide-react';
import { Fragment, useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import {
  changeMember,
  listMembers,
  type Member,
  type Organization,
  ORGANIZATION_ROLE_LABELS,
  removeMember,
  transferOwnership,
} from '@/api/organizations';
import { useSession } from '@/auth/session';
import { ErrorState, LoadingState } from '@/components/common/feedback';
import { UserAvatar } from '@/components/common/user';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';
import { InviteMemberDialog } from './InviteMemberDialog';
import { ROLE_BADGE } from './organizationDisplay';

const headClass = 'h-10 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase';

/** Thao tác cần hỏi lại trước khi làm. */
type Confirm = {
  title: string;
  description: string;
  confirm: string;
  success: string;
  destructive?: boolean;
  run: () => Promise<unknown>;
};

type MembersPanelProps = {
  organization: Organization;
  /** Vai trò của mình thay đổi (chuyển quyền sở hữu) hoặc mình rời tổ chức. */
  onOrganizationChanged: (left: boolean) => void;
};

/**
 * Thành viên và phân quyền theo BR-14: OWNER đổi vai trò, cấp quyền tạo sự kiện, chuyển quyền sở hữu, xóa ai cũng
 * được; MANAGER mời và xóa Thành viên; ai cũng tự rời được trừ OWNER.
 */
export function MembersPanel({ organization, onOrganizationChanged }: MembersPanelProps) {
  const session = useSession();
  const me = session?.user.id;
  const isOwner = organization.myRole === 'OWNER';
  const canInvite = organization.myRole !== 'MEMBER';
  const [members, setMembers] = useState<Member[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [inviting, setInviting] = useState(false);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    setLoadError(null);
    listMembers(organization.id)
      .then((data) => !ignore && setMembers(data))
      .catch((err: unknown) => !ignore && setLoadError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [organization.id, reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);
  // Số thành viên ở đầu trang cũng phải cập nhật.
  const membersChanged = () => {
    reload();
    onOrganizationChanged(false);
  };

  const run = useCallback(async (action: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      if (success) toast.success(success);
      return true;
    } catch (err) {
      setActionError(errorMessage(err));
      return false;
    } finally {
      setBusy(false);
    }
  }, []);

  const name = (member: Member) => member.fullName ?? member.email ?? 'Người dùng';

  function menuFor(member: Member) {
    const self = member.userId === me;
    const items: { key: string; label: string; icon: typeof Shield; destructive?: boolean; onSelect: () => void }[] = [];
    if (isOwner && member.role !== 'OWNER') {
      const other = member.role === 'MANAGER' ? 'MEMBER' : 'MANAGER';
      items.push({
        key: 'role',
        label: other === 'MANAGER' ? 'Đặt làm Quản lý' : 'Đặt làm Thành viên',
        icon: other === 'MANAGER' ? Shield : ShieldOff,
        onSelect: () =>
          void run(
            () => changeMember(organization.id, member.userId, { role: other, canCreateEvents: false }),
            `${name(member)} giờ là ${ORGANIZATION_ROLE_LABELS[other]}`,
          ).then(
            (ok) => ok && reload(),
          ),
      });
      if (member.role === 'MEMBER') {
        items.push({
          key: 'grant',
          label: member.canCreateEvents ? 'Thu hồi quyền tạo sự kiện' : 'Cấp quyền tạo sự kiện',
          icon: KeyRound,
          onSelect: () =>
            void run(() =>
              changeMember(organization.id, member.userId, { role: 'MEMBER', canCreateEvents: !member.canCreateEvents }),
              member.canCreateEvents
                ? `Đã thu hồi quyền tạo sự kiện của ${name(member)}`
                : `Đã cấp quyền tạo sự kiện cho ${name(member)}`,
            ).then((ok) => ok && reload()),
        });
      }
      items.push({
        key: 'owner',
        label: 'Chuyển quyền sở hữu',
        icon: Crown,
        onSelect: () =>
          setConfirm({
            title: 'Chuyển quyền sở hữu?',
            description: `${name(member)} sẽ thành Chủ sở hữu. Bạn chuyển xuống Quản lý và không còn sửa tổ chức hay phân quyền được nữa.`,
            confirm: 'Chuyển quyền',
            success: `${name(member)} giờ là Chủ sở hữu`,
            run: async () => {
              await transferOwnership(organization.id, member.userId);
              onOrganizationChanged(false);
            },
          }),
      });
    }
    const canRemove =
      !self && member.role !== 'OWNER' && (isOwner || (organization.myRole === 'MANAGER' && member.role === 'MEMBER'));
    if (canRemove) {
      items.push({
        key: 'remove',
        label: 'Xóa khỏi tổ chức',
        icon: Trash2,
        destructive: true,
        onSelect: () =>
          setConfirm({
            title: 'Xóa khỏi tổ chức?',
            description: `${name(member)} sẽ không còn thấy và quản lý sự kiện của tổ chức. Nếu họ đang phụ trách sự kiện, hãy đổi người phụ trách trước.`,
            confirm: 'Xóa',
            success: `Đã xóa ${name(member)} khỏi tổ chức`,
            destructive: true,
            run: async () => {
              await removeMember(organization.id, member.userId);
              membersChanged();
            },
          }),
      });
    }
    if (self && member.role !== 'OWNER') {
      items.push({
        key: 'leave',
        label: 'Rời tổ chức',
        icon: LogOut,
        destructive: true,
        onSelect: () =>
          setConfirm({
            title: 'Rời tổ chức?',
            description: 'Bạn sẽ không còn thấy sự kiện của tổ chức. Muốn quay lại thì nhờ Chủ sở hữu hoặc Quản lý mời lại.',
            confirm: 'Rời tổ chức',
            success: `Đã rời ${organization.name}`,
            destructive: true,
            run: async () => {
              await removeMember(organization.id, member.userId);
              onOrganizationChanged(true);
            },
          }),
      });
    }
    return items;
  }

  return (
    <Card className="gap-0 overflow-hidden rounded-xl py-0 shadow-sm">
      <div className="flex flex-col gap-3 border-b border-border/50 p-4 md:flex-row md:items-center md:justify-between">
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Users className="size-4" aria-hidden />
          {members ? `${members.length} thành viên` : 'Thành viên'}
          {organization.myRole === 'MEMBER' && ' · Chỉ Chủ sở hữu và Quản lý mời được người mới'}
        </p>
        {canInvite && (
          <Button onClick={() => setInviting(true)} className="h-9 self-end rounded-lg px-3 font-semibold md:self-auto">
            <UserPlus />
            Mời thành viên
          </Button>
        )}
      </div>

      {actionError && (
        <p role="alert" className="m-4 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          {actionError}
        </p>
      )}

      {loadError ? (
        <ErrorState description={loadError} onRetry={reload} />
      ) : !members ? (
        <div className="p-6">
          <LoadingState lines={4} />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <Table aria-busy={busy} className={cn(busy && 'opacity-60 transition-opacity')}>
            <TableHeader className="border-b border-border/50 bg-surface-alt/50">
              <TableRow className="hover:bg-transparent">
                <TableHead className={cn(headClass, 'px-4')}>Thành viên</TableHead>
                <TableHead className={headClass}>Vai trò</TableHead>
                <TableHead className={headClass}>Tạo sự kiện</TableHead>
                <TableHead className={cn(headClass, 'px-4 text-right')}>Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map((member) => {
                const items = menuFor(member);
                return (
                  <TableRow key={member.userId} className="border-b border-border/40 hover:bg-accent/30">
                    <TableCell className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <UserAvatar user={{ name: name(member), email: member.email ?? '' }} />
                        <div className="min-w-0">
                          <p className="truncate font-semibold">
                            {name(member)}
                            {member.userId === me && <span className="font-normal text-muted-foreground"> (bạn)</span>}
                          </p>
                          {member.email && <p className="truncate text-xs text-muted-foreground">{member.email}</p>}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="py-3">
                      <Badge variant={ROLE_BADGE[member.role]} size="label">
                        {ORGANIZATION_ROLE_LABELS[member.role]}
                      </Badge>
                    </TableCell>
                    <TableCell className="py-3 text-[13px]">
                      {member.canCreateEvents ? (
                        <span className="font-medium text-success">Được tạo</span>
                      ) : (
                        <span className="text-muted-foreground">Chưa được cấp quyền</span>
                      )}
                    </TableCell>
                    <TableCell className="px-4 py-3 text-right">
                      {items.length > 0 && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              disabled={busy}
                              className="size-9 rounded-full hover:text-foreground"
                              aria-label={`Thao tác với ${name(member)}`}
                            >
                              <MoreVertical className="size-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-56">
                            {items.map((item, index) => (
                              <Fragment key={item.key}>
                                {item.destructive && index > 0 && !items[index - 1].destructive && <DropdownMenuSeparator />}
                                <DropdownMenuItem
                                  variant={item.destructive ? 'destructive' : 'default'}
                                  onSelect={item.onSelect}
                                >
                                  <item.icon />
                                  {item.label}
                                </DropdownMenuItem>
                              </Fragment>
                            ))}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      <InviteMemberDialog open={inviting} onOpenChange={setInviting} organization={organization} onInvited={membersChanged} />

      <AlertDialog open={confirm !== null} onOpenChange={(open) => !open && !busy && setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{confirm?.title}</AlertDialogTitle>
            <AlertDialogDescription>{confirm?.description}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              variant={confirm?.destructive ? 'destructive' : 'default'}
              disabled={busy}
              onClick={(e) => {
                // Giữ hộp thoại mở tới khi có kết quả.
                e.preventDefault();
                if (confirm) void run(confirm.run, confirm.success).then(() => setConfirm(null));
              }}
            >
              {confirm?.confirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
