import { RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';

import type { EventStatus } from '@/api/events';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EVENT_STATUS_LABELS } from '@/lib/eventStatus';
import { cn } from '@/lib/utils';

const SEARCH_DEBOUNCE_MS = 300;

type EventListToolbarProps = {
  /** Bỏ trống thì không có ô tìm (API /my không tìm theo từ khóa). */
  keyword?: string;
  onKeywordChange?: (keyword: string) => void;
  /** Bỏ trống thì không có tab lọc nhanh. */
  statuses?: EventStatus[];
  status?: EventStatus;
  onStatusChange?: (status: EventStatus | undefined) => void;
  onRefresh: () => void;
  refreshing: boolean;
};

/** Thanh lọc trên bảng sự kiện, theo EventListToolbar của openevent. */
export function EventListToolbar({
  keyword,
  onKeywordChange,
  statuses,
  status,
  onStatusChange,
  onRefresh,
  refreshing,
}: EventListToolbarProps) {
  const [search, setSearch] = useState(keyword ?? '');

  // Bộ lọc bị xóa từ ngoài (nút "Xóa bộ lọc" của EmptyState) thì ô tìm cũng xóa theo.
  useEffect(() => {
    setSearch((current) => (current.trim() === (keyword ?? '') ? current : (keyword ?? '')));
  }, [keyword]);

  // Gõ xong 300ms mới tìm, không gọi API theo từng phím.
  useEffect(() => {
    if (!onKeywordChange || search.trim() === keyword) return;
    const timer = setTimeout(() => onKeywordChange(search.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search, keyword, onKeywordChange]);

  const hasFilters = Boolean(keyword) || status !== undefined;

  return (
    <div className="flex flex-col gap-3 border-b border-border/50 p-4 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 flex-col gap-3 md:flex-row md:items-center">
        {onKeywordChange && (
          <div className="relative w-full md:w-80 md:shrink-0">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              aria-label="Tìm sự kiện"
              placeholder="Tìm theo tên sự kiện..."
              maxLength={100}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-9 rounded-lg bg-surface-alt pr-3 pl-9 text-sm shadow-none focus-visible:bg-card focus-visible:ring-1"
            />
          </div>
        )}
        {statuses && onStatusChange && (
          <div className="flex flex-wrap items-center gap-1 rounded-lg border bg-surface-alt p-1" role="group" aria-label="Lọc nhanh">
            {[undefined, ...statuses].map((value) => {
              const active = status === value;
              return (
                <button
                  key={value ?? 'ALL'}
                  type="button"
                  aria-pressed={active}
                  onClick={() => onStatusChange(value)}
                  className={cn(
                    'h-7 rounded-md px-3 text-xs transition-colors',
                    active
                      ? 'bg-card font-semibold text-accent-foreground shadow-sm'
                      : 'font-medium text-muted-foreground hover:text-foreground',
                  )}
                >
                  {value ? EVENT_STATUS_LABELS[value] : 'Tất cả'}
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="flex items-center gap-2 self-end md:self-auto">
        {hasFilters && (
          <Button
            variant="ghost"
            size="sm"
            className="h-9 rounded-lg text-xs text-muted-foreground"
            onClick={() => {
              onKeywordChange?.('');
              onStatusChange?.(undefined);
            }}
          >
            <X className="size-4" />
            Xóa bộ lọc
          </Button>
        )}
        <Button
          variant="outline"
          size="icon"
          className="size-9 shrink-0 rounded-lg bg-card shadow-sm hover:bg-surface-alt"
          aria-label="Tải lại danh sách sự kiện"
          onClick={onRefresh}
          disabled={refreshing}
        >
          <RefreshCw className={cn('size-4 text-muted-foreground', refreshing && 'animate-spin')} />
        </Button>
      </div>
    </div>
  );
}
