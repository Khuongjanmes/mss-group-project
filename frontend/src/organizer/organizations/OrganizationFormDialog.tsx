import { type FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import { createOrganization, type Organization, updateOrganization } from '@/api/organizations';
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
import { Spinner } from '@/components/ui/spinner';
import { Textarea } from '@/components/ui/textarea';
import { FieldErrorText } from '../event-form/FieldErrorText';

const LABEL = 'mb-1.5 block text-sm font-semibold text-foreground';

type OrganizationFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Có thì sửa tổ chức này (chỉ OWNER), không thì tạo mới. */
  organization?: Organization;
  onSaved: (organization: Organization) => void;
};

export function OrganizationFormDialog({ open, onOpenChange, organization, onSaved }: OrganizationFormDialogProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(organization?.name ?? '');
    setDescription(organization?.description ?? '');
    setErrors({});
    setSubmitError(null);
  }, [open, organization]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setErrors({ name: 'Nhập tên tổ chức' });
      return;
    }
    setSaving(true);
    setSubmitError(null);
    try {
      const body = { name: name.trim(), description: description.trim() || null };
      const saved = organization ? await updateOrganization(organization.id, body) : await createOrganization(body);
      onOpenChange(false);
      toast.success(organization ? 'Đã lưu thông tin tổ chức' : `Đã tạo tổ chức "${saved.name}"`);
      onSaved(saved);
    } catch (err) {
      setErrors(fieldErrors(err));
      setSubmitError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !saving && onOpenChange(next)}>
      <DialogContent className="max-w-lg rounded-xl bg-card shadow-2xl">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">{organization ? 'Sửa tổ chức' : 'Tạo tổ chức mới'}</DialogTitle>
            <DialogDescription>
              {organization
                ? 'Tên và mô tả hiển thị cho các thành viên.'
                : 'Bạn sẽ là Chủ sở hữu, rồi mời thêm Quản lý và Thành viên.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4 text-sm">
            <div>
              <label htmlFor="organization-name" className={LABEL}>
                Tên tổ chức <span className="text-destructive">*</span>
              </label>
              <Input
                id="organization-name"
                placeholder="Ví dụ: FPT Event Club"
                className="bg-background"
                maxLength={200}
                aria-invalid={Boolean(errors.name) || undefined}
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  setErrors({});
                }}
              />
              <FieldErrorText id="organization-name-error" message={errors.name} />
            </div>
            <div>
              <label htmlFor="organization-description" className={LABEL}>
                Mô tả
              </label>
              <Textarea
                id="organization-description"
                rows={4}
                maxLength={2000}
                className="bg-background"
                placeholder="Giới thiệu ngắn về câu lạc bộ, đơn vị tổ chức…"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            {submitError && (
              <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-destructive">
                {submitError}
              </p>
            )}
          </div>

          <DialogFooter className="mt-2 gap-2 sm:gap-0">
            <Button type="button" variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving && <Spinner />}
              {organization ? 'Lưu thay đổi' : 'Tạo tổ chức'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
