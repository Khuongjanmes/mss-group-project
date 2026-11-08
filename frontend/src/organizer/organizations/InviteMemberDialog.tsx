import { type FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { inviteMember, type Member, type Organization, ORGANIZATION_ROLE_LABELS } from '@/api/organizations';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { ROLE_DESCRIPTIONS } from './organizationDisplay';

type InvitableRole = 'MANAGER' | 'MEMBER';

const LABEL = 'mb-1.5 block text-sm font-semibold text-foreground';

type InviteMemberDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  organization: Organization;
  onInvited: (member: Member) => void;
};

/** OWNER mời Quản lý hoặc Thành viên; MANAGER chỉ mời Thành viên (BR-14). */
export function InviteMemberDialog({ open, onOpenChange, organization, onInvited }: InviteMemberDialogProps) {
  const roles: InvitableRole[] = organization.myRole === 'OWNER' ? ['MEMBER', 'MANAGER'] : ['MEMBER'];
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<InvitableRole>('MEMBER');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setEmail('');
    setRole('MEMBER');
    setError(null);
  }, [open]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Nhập email hợp lệ');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const member = await inviteMember(organization.id, email.trim(), role);
      onOpenChange(false);
      toast.success(`Đã thêm ${member.fullName ?? member.email ?? 'thành viên'} vào tổ chức`);
      onInvited(member);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !saving && onOpenChange(next)}>
      <DialogContent className="max-w-lg rounded-xl bg-card shadow-2xl">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">Mời thành viên</DialogTitle>
            <DialogDescription>
              Nhập email tài khoản OpenEvent của người cần mời. Họ vào tổ chức ngay, không cần xác nhận.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4 text-sm">
            <div>
              <label htmlFor="invite-email" className={LABEL}>
                Email <span className="text-destructive">*</span>
              </label>
              <Input
                id="invite-email"
                type="email"
                autoComplete="off"
                placeholder="ban@example.com"
                className="bg-background"
                maxLength={254}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="invite-role" className={LABEL}>
                Vai trò
              </label>
              <Select value={role} onValueChange={(value) => setRole(value as InvitableRole)} disabled={roles.length === 1}>
                <SelectTrigger id="invite-role" className="w-full bg-background">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {roles.map((value) => (
                    <SelectItem key={value} value={value}>
                      {ORGANIZATION_ROLE_LABELS[value]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="mt-1 text-xs text-muted-foreground">{ROLE_DESCRIPTIONS[role]}</p>
              {role === 'MEMBER' && (
                <p className="mt-1 text-xs text-muted-foreground">
                  Thành viên mới chưa được tạo sự kiện; Chủ sở hữu cấp quyền sau.
                </p>
              )}
            </div>
            {error && (
              <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-destructive">
                {error}
              </p>
            )}
          </div>

          <DialogFooter className="mt-2 gap-2 sm:gap-0">
            <Button type="button" variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving && <Spinner />}
              Mời
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
