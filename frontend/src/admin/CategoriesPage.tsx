import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage, fieldErrors } from '@/api/client';
import { type Category, createCategory, deleteCategory, listCategories, updateCategory } from '@/api/events';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { ConfirmDeleteDialog } from './ConfirmDeleteDialog';

/** Bỏ dấu để tìm "hoi thao" vẫn ra "Hội thảo". */
const fold = (value: string) =>
  value.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();

/** Admin #3: danh mục để gán cho sự kiện và để người mua vé lọc, theo trang Thể loại sự kiện của openevent. */
export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);

  useEffect(() => {
    let ignore = false;
    setError(null);
    listCategories()
      .then((data) => !ignore && setCategories(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);
  const visible = useMemo(() => {
    const query = fold(search.trim());
    return (categories ?? []).filter((c) => !query || fold(c.name).includes(query) || c.slug.includes(query));
  }, [categories, search]);

  const addButton = (
    <Button onClick={() => setEditing('new')}>
      <Plus />
      Thêm danh mục
    </Button>
  );

  return (
    <div className="space-y-6 text-sm">
      <PageHeader
        title="Danh mục sự kiện"
        description="Danh mục để ban tổ chức gán cho sự kiện và để người mua vé lọc. Danh mục đang có sự kiện dùng thì không xóa được."
        actions={addButton}
      />

      <div className="relative w-full max-w-sm">
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          type="search"
          aria-label="Tìm danh mục"
          placeholder="Tìm theo tên hoặc slug"
          className="pl-9"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <Card className="py-0">
        <CardContent className="px-0">
          {error ? (
            <ErrorState description={error} onRetry={reload} />
          ) : !categories ? (
            <div className="p-6">
              <LoadingState lines={5} />
            </div>
          ) : visible.length === 0 ? (
            search ? (
              <EmptyState title="Không có danh mục phù hợp" description="Thử từ khóa khác." />
            ) : (
              <EmptyState
                title="Chưa có danh mục nào"
                description="Sự kiện cần có danh mục mới tạo được. Hãy thêm danh mục đầu tiên."
                action={addButton}
              />
            )
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Tên</TableHead>
                  <TableHead>Slug</TableHead>
                  <TableHead>Mô tả</TableHead>
                  <TableHead className="pr-6 text-right">
                    <span className="sr-only">Thao tác</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {visible.map((category) => (
                  <TableRow key={category.id}>
                    <TableCell className="pl-6 font-medium">{category.name}</TableCell>
                    <TableCell>
                      <code className="text-xs text-muted-foreground">{category.slug}</code>
                    </TableCell>
                    <TableCell className="max-w-96 truncate text-muted-foreground" title={category.description ?? undefined}>
                      {category.description || '—'}
                    </TableCell>
                    <TableCell className="pr-6">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" aria-label={`Sửa ${category.name}`} onClick={() => setEditing(category)}>
                          <Pencil />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:text-destructive"
                          aria-label={`Xóa ${category.name}`}
                          onClick={() => setDeleting(category)}
                        >
                          <Trash2 />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {editing && (
        <CategoryFormDialog
          category={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={reload}
        />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          title={`Xóa danh mục "${deleting.name}"?`}
          description="Xóa rồi không khôi phục được. Danh mục đang có sự kiện dùng thì không xóa được."
          onConfirm={async () => {
            await deleteCategory(deleting.id);
            toast.success(`Đã xóa danh mục "${deleting.name}"`);
            reload();
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  );
}

function CategoryFormDialog({
  category,
  onClose,
  onSaved,
}: {
  category?: Category;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setErrors({ name: 'Nhập tên danh mục' });
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const body = { name: name.trim(), description: description.trim() || null };
      if (category) await updateCategory(category.id, body);
      else await createCategory(body);
      toast.success(category ? 'Đã lưu danh mục' : `Đã thêm danh mục "${body.name}"`);
      onSaved();
      onClose();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !saving && onClose()}>
      <DialogContent className="max-w-md">
        <form onSubmit={(e) => void submit(e)} noValidate>
          <DialogHeader>
            <DialogTitle>{category ? 'Sửa danh mục' : 'Thêm danh mục sự kiện'}</DialogTitle>
            <DialogDescription>
              Slug dùng trong đường dẫn lọc sự kiện, tự sinh từ tên (ví dụ "Hội thảo" → <code>hoi-thao</code>).
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4 text-sm">
            <div className="space-y-1.5">
              <label htmlFor="category-name" className="block font-semibold">
                Tên danh mục <span className="text-destructive">*</span>
              </label>
              <Input
                id="category-name"
                autoFocus
                maxLength={100}
                placeholder="Ví dụ: Hội thảo"
                aria-invalid={Boolean(errors.name) || undefined}
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  setErrors({});
                }}
              />
              {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
            </div>
            <div className="space-y-1.5">
              <label htmlFor="category-description" className="block font-semibold">
                Mô tả
              </label>
              <Textarea
                id="category-description"
                rows={3}
                maxLength={1000}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            {error && (
              <p role="alert" className="font-medium text-destructive">
                {error}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={saving} onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving && <Spinner />}
              {category ? 'Lưu' : 'Tạo danh mục'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
