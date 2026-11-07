import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { PageResponse } from '@/api/types';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { formatNumber, pageItems } from '@/lib/pagination';
import { cn } from '@/lib/utils';

type ListPaginationProps = {
  meta: Omit<PageResponse<unknown>, 'content'>;
  onPageChange: (page: number) => void;
  /** What the list counts, e.g. "sự kiện", "người". */
  unit: string;
  /** Rows-per-page choices; the selector shows only when `onPageSizeChange` is given too. */
  pageSizes?: number[];
  onPageSizeChange?: (size: number) => void;
};

const pageButton =
  'h-9 w-9 rounded-lg border-border bg-card font-semibold text-foreground shadow-sm hover:bg-surface-alt';

/** "Hiển thị 1 - 20 của 92 người", optional rows-per-page selector and numbered page buttons. */
export function ListPagination({
  meta,
  onPageChange,
  unit,
  pageSizes,
  onPageSizeChange,
}: ListPaginationProps) {
  const first = meta.totalElements === 0 ? 0 : meta.page * meta.size + 1;
  const last = Math.min((meta.page + 1) * meta.size, meta.totalElements);

  return (
    <nav
      className="flex flex-col items-center justify-between gap-3 border-t border-border/50 px-4 py-3 text-xs text-muted-foreground sm:flex-row"
      aria-label="Phân trang"
    >
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="font-medium">
          Hiển thị{' '}
          <span className="font-bold text-foreground">
            {formatNumber(first)} - {formatNumber(last)}
          </span>{' '}
          của <span className="font-bold text-foreground">{formatNumber(meta.totalElements)}</span>{' '}
          {unit}
        </div>
        {pageSizes && onPageSizeChange && (
          <Select
            value={String(meta.size)}
            onValueChange={(value) => onPageSizeChange(Number(value))}
          >
            <SelectTrigger size="sm" className="h-8 gap-1 text-xs" aria-label="Số dòng mỗi trang">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageSizes.map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size} / trang
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>
      {meta.totalPages > 1 && (
        <div className="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="icon"
            className={pageButton}
            disabled={meta.page === 0}
            onClick={() => onPageChange(meta.page - 1)}
            aria-label="Trang trước"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          {pageItems(meta.page, meta.totalPages).map((page, index) =>
            page === null ? (
              <span key={`gap-${index}`} className="px-1 font-bold text-muted-foreground">
                …
              </span>
            ) : (
              <Button
                key={page}
                variant="outline"
                size="icon"
                aria-label={`Trang ${page + 1}`}
                aria-current={page === meta.page ? 'page' : undefined}
                className={cn(
                  pageButton,
                  page === meta.page &&
                    'border-primary bg-primary font-bold text-primary-foreground shadow-md hover:bg-primary/90 hover:text-primary-foreground',
                )}
                onClick={() => onPageChange(page)}
              >
                {page + 1}
              </Button>
            ),
          )}
          <Button
            variant="outline"
            size="icon"
            className={pageButton}
            disabled={meta.page + 1 >= meta.totalPages}
            onClick={() => onPageChange(meta.page + 1)}
            aria-label="Trang sau"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </nav>
  );
}
