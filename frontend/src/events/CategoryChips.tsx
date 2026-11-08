import type { Category } from '@/api/events';
import { cn } from '@/lib/utils';

type CategoryChipsProps = {
  categories: Category[];
  /** Slug đang chọn; rỗng là "Tất cả". */
  value: string;
  onChange: (slug: string) => void;
};

/** Hàng chip danh mục theo CategoryChips của openevent SBA301; màn hình hẹp thì cuộn ngang. */
export function CategoryChips({ categories, value, onChange }: CategoryChipsProps) {
  const chips = [{ slug: '', name: 'Tất cả' }, ...categories];

  return (
    <div role="group" aria-label="Lọc theo danh mục" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
      {chips.map((chip) => {
        const active = chip.slug === value;
        return (
          <button
            key={chip.slug || 'all'}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(chip.slug)}
            className={cn(
              'h-8 shrink-0 rounded-full border px-3.5 text-[13px] font-medium whitespace-nowrap transition-colors',
              active
                ? 'border-primary bg-primary text-primary-foreground shadow-sm'
                : 'border-border bg-card text-muted-foreground hover:border-primary/40 hover:text-foreground',
            )}
          >
            {chip.name}
          </button>
        );
      })}
    </div>
  );
}
