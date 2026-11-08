import { Edit2, ImageIcon, Loader2, Trash2 } from 'lucide-react';
import { type DragEvent, useRef, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import { uploadEventImage } from '@/api/events';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { EventSectionCard } from './EventSectionCard';
import type { EventFieldErrors, EventForm } from './eventForm';
import { FieldErrorText } from './FieldErrorText';

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_SIZE = 5 * 1024 * 1024;

type EventMediaCardProps = {
  eventId: string;
  form: EventForm;
  setField: (field: keyof EventForm, value: string) => void;
  errors: EventFieldErrors;
  disabled: boolean;
};

/**
 * Ảnh bìa theo EventMediaCard của openevent: kéo thả hoặc bấm để tải lên Cloudinary, hoặc dán đường dẫn ảnh.
 * Ảnh tải lên chỉ gắn vào sự kiện khi bấm "Lưu thay đổi".
 */
export function EventMediaCard({ eventId, form, setField, errors, disabled }: EventMediaCardProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);
  const url = form.bannerUrl.trim();
  const showImage = /^https?:\/\/\S+$/i.test(url) && brokenUrl !== url;
  const busy = disabled || uploading;

  async function upload(file: File) {
    setUploadError(null);
    // Kiểm tra trước cho nhanh; server kiểm tra lại cả nội dung tệp.
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setUploadError('Chỉ hỗ trợ ảnh JPG, PNG hoặc WEBP');
      return;
    }
    if (file.size > MAX_SIZE) {
      setUploadError('Dung lượng ảnh không được vượt quá 5 MB');
      return;
    }
    setUploading(true);
    try {
      const uploaded = await uploadEventImage(eventId, file);
      setField('bannerUrl', uploaded.url);
      toast.success('Đã tải ảnh lên. Bấm "Lưu thay đổi" để gắn vào sự kiện.');
    } catch (err) {
      setUploadError(errorMessage(err));
    } finally {
      setUploading(false);
    }
  }

  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (file && !busy) void upload(file);
  }

  return (
    <EventSectionCard title="Truyền thông & Hình ảnh" contentClassName="space-y-4 pb-8">
      <label className="block text-[12px] font-bold tracking-wider text-foreground uppercase">
        Ảnh bìa chính (Banner 21:9)
      </label>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED_TYPES.join(',')}
        aria-label="Chọn ảnh bìa"
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) void upload(file);
          event.target.value = '';
        }}
      />
      <div
        className={cn(
          'group relative overflow-hidden rounded-2xl border bg-muted/20',
          !showImage && !disabled && 'border-dashed border-primary/40',
        )}
        onDragOver={(event) => event.preventDefault()}
        onDrop={drop}
      >
        {showImage ? (
          <>
            <img
              src={url}
              alt="Ảnh bìa sự kiện"
              onError={() => setBrokenUrl(url)}
              className="aspect-[21/9] w-full object-cover"
            />
            {!disabled && (
              <div className="absolute inset-x-0 bottom-0 flex flex-col justify-between gap-4 bg-linear-to-t from-black/80 via-black/40 to-transparent p-4 md:flex-row md:items-end md:p-5">
                <div className="min-w-0">
                  <p className="mb-1 line-clamp-1 text-[15px] leading-tight font-bold text-white">
                    {url.split('/').pop() || 'banner'}
                  </p>
                  <p className="text-[11px] font-medium text-white/70">Banner tỉ lệ 21:9 · nên từ 2520 × 1080 px</p>
                </div>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    className="h-9 rounded-lg border-none bg-card px-4 text-[13px] font-bold text-primary shadow-sm hover:bg-accent"
                    disabled={busy}
                    onClick={() => inputRef.current?.click()}
                  >
                    {uploading ? <Loader2 className="animate-spin" /> : <Edit2 />}
                    Thay đổi ảnh
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="destructive"
                    className="h-9 rounded-lg px-4 text-[13px] font-bold shadow-sm"
                    disabled={busy}
                    onClick={() => setField('bannerUrl', '')}
                  >
                    <Trash2 />
                    Xóa ảnh
                  </Button>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="flex aspect-[21/6] min-h-56 flex-col items-center justify-center gap-3 p-8 text-muted-foreground">
            <div className="flex size-16 items-center justify-center rounded-full bg-primary/10 text-primary">
              {uploading ? <Loader2 className="size-8 animate-spin" /> : <ImageIcon className="size-8" />}
            </div>
            {disabled ? (
              <p className="text-sm">Chưa có ảnh bìa</p>
            ) : (
              <>
                <div className="space-y-1 text-center">
                  <p className="font-bold text-foreground">{uploading ? 'Đang tải ảnh lên…' : 'Kéo thả ảnh vào đây'}</p>
                  <p className="text-xs">
                    hoặc{' '}
                    <button
                      type="button"
                      className="cursor-pointer text-primary hover:underline disabled:cursor-not-allowed"
                      disabled={busy}
                      onClick={() => inputRef.current?.click()}
                    >
                      nhấn để tải lên
                    </button>
                  </p>
                </div>
                <Input
                  type="url"
                  aria-label="Đường dẫn ảnh bìa"
                  placeholder="Hoặc nhập URL ảnh..."
                  maxLength={500}
                  disabled={busy}
                  aria-invalid={Boolean(errors.bannerUrl) || undefined}
                  value={form.bannerUrl}
                  onChange={(e) => setField('bannerUrl', e.target.value)}
                  className="mt-2 max-w-md rounded-xl bg-background"
                />
                {url && brokenUrl === url && <p className="text-xs text-destructive">Không tải được ảnh từ đường dẫn này</p>}
              </>
            )}
          </div>
        )}
      </div>
      <FieldErrorText id="event-banner-error" message={errors.bannerUrl} />
      {uploadError && (
        <p role="alert" className="text-sm text-destructive">
          {uploadError}
        </p>
      )}
      <p className="text-[11px] font-medium text-muted-foreground">
        Định dạng hỗ trợ: JPG, PNG hoặc WEBP. Dung lượng tối đa 5 MB. Ảnh mới chỉ gắn vào sự kiện sau khi bấm "Lưu thay
        đổi".
      </p>
    </EventSectionCard>
  );
}
