import type { Category } from '@/api/events';
import { RichTextEditor } from '@/components/common/editor/RichTextEditor';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EventSectionCard } from './EventSectionCard';
import { type EventFieldErrors, type EventForm, TITLE_MAX_LENGTH } from './eventForm';
import { FIELD_CLASS, FIELD_LABEL_CLASS, LABEL_TEXT_CLASS, SELECT_TRIGGER_CLASS } from './fieldStyles';
import { FieldErrorText } from './FieldErrorText';

type EventGeneralInfoCardProps = {
  form: EventForm;
  setField: (field: keyof EventForm, value: string) => void;
  categories: Category[];
  errors: EventFieldErrors;
  disabled: boolean;
};

export function EventGeneralInfoCard({ form, setField, categories, errors, disabled }: EventGeneralInfoCardProps) {
  return (
    <EventSectionCard title="Thông tin chung" aside="* Các mục đánh dấu sao là bắt buộc">
      <div>
        <div className="mb-2 flex justify-between">
          <label htmlFor="event-title" className={LABEL_TEXT_CLASS}>
            Tên sự kiện <span className="text-destructive">*</span>
          </label>
          <span className="text-[11px] font-medium text-muted-foreground">
            {form.title.length}/{TITLE_MAX_LENGTH} ký tự
          </span>
        </div>
        <Input
          id="event-title"
          aria-required
          aria-invalid={Boolean(errors.title) || undefined}
          aria-describedby={errors.title ? 'event-title-error' : undefined}
          maxLength={TITLE_MAX_LENGTH}
          disabled={disabled}
          value={form.title}
          onChange={(e) => setField('title', e.target.value)}
          className={`${FIELD_CLASS} px-4 font-medium`}
        />
        <FieldErrorText id="event-title-error" message={errors.title} />
      </div>

      <div className="md:w-1/2 md:pr-3">
        <label htmlFor="event-category" className={FIELD_LABEL_CLASS}>
          Danh mục sự kiện <span className="text-destructive">*</span>
        </label>
        <Select value={form.categoryId} disabled={disabled} onValueChange={(value) => setField('categoryId', value)}>
          <SelectTrigger
            id="event-category"
            aria-invalid={Boolean(errors.categoryId) || undefined}
            className={SELECT_TRIGGER_CLASS}
          >
            <SelectValue placeholder="Chọn danh mục sự kiện" />
          </SelectTrigger>
          <SelectContent>
            {categories.map((category) => (
              <SelectItem key={category.id} value={category.id}>
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <FieldErrorText id="event-category-error" message={errors.categoryId} />
        {categories.length === 0 && (
          <p className="mt-2 text-xs text-muted-foreground">Chưa có danh mục nào. Cần tạo danh mục trước.</p>
        )}
      </div>

      <div>
        <label htmlFor="event-description" className={FIELD_LABEL_CLASS}>
          Mô tả chi tiết sự kiện
        </label>
        <RichTextEditor
          id="event-description"
          label="Mô tả chi tiết sự kiện"
          disabled={disabled}
          invalid={Boolean(errors.description)}
          value={form.description}
          onChange={(html) => setField('description', html)}
        />
        <FieldErrorText id="event-description-error" message={errors.description} />
      </div>
    </EventSectionCard>
  );
}
