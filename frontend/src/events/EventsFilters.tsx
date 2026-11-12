import { Calendar, CalendarClock, History, Layers, LayoutList, Radio, RotateCcw, Search, type LucideIcon } from 'lucide-react';
import { useEffect, useState } from 'react';

import type { Category } from '@/api/events';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { cn } from '@/lib/utils';
import { TIME_PRESETS, type TimePreset } from './eventTimeRange';

export const EVENT_TIMEFRAMES = ['all', 'upcoming', 'ongoing', 'ended'] as const;
export type EventTimeframe = (typeof EVENT_TIMEFRAMES)[number];

export type EventsFilterValues = {
  keyword: string;
  timeframe: EventTimeframe;
  /** Slug danh mục. */
  category?: string;
  time?: TimePreset;
};

const ALL = 'all';
const SEARCH_DEBOUNCE_MS = 300;

const TIMEFRAME_LABELS: Record<EventTimeframe, string> = {
  all: 'Tất cả',
  upcoming: 'Sắp tới',
  ongoing: 'Đang diễn ra',
  ended: 'Đã kết thúc',
};

const TIMEFRAME_ICONS: Record<EventTimeframe, LucideIcon> = {
  all: LayoutList,
  upcoming: CalendarClock,
  ongoing: Radio,
  ended: History,
};

type Props = {
  filters: EventsFilterValues;
  onChange: (changes: Partial<EventsFilterValues>) => void;
  onReset: () => void;
  categories: Category[];
};

/** Bộ lọc trang sự kiện theo EventsFilters của openevent: tab trạng thái dạng pill, ô tìm kiếm, thể loại, thời gian. */
export function EventsFilters({ filters, onChange, onReset, categories }: Props) {
  const [search, setSearch] = useState(filters.keyword);
  const [syncedKeyword, setSyncedKeyword] = useState(filters.keyword);

  // Đặt lại bộ lọc hoặc quay lại trang trước thì ô tìm theo URL.
  if (filters.keyword !== syncedKeyword) {
    setSyncedKeyword(filters.keyword);
    if (search.trim() !== filters.keyword) setSearch(filters.keyword);
  }

  useEffect(() => {
    if (search.trim() === filters.keyword) return;
    const timer = setTimeout(() => onChange({ keyword: search.trim() }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search, filters.keyword, onChange]);

  const hasActiveFilters =
    filters.keyword !== '' || filters.timeframe !== 'all' || filters.category !== undefined || filters.time !== undefined;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-1.5 overflow-x-auto py-0.5" role="group" aria-label="Lọc theo trạng thái sự kiện">
          {EVENT_TIMEFRAMES.map((tf) => {
            const active = filters.timeframe === tf;
            const Icon = TIMEFRAME_ICONS[tf];
            return (
              <button
                key={tf}
                type="button"
                aria-pressed={active}
                onClick={() => onChange({ timeframe: tf })}
                className={cn(
                  'inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-full px-3 text-xs font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring/50 sm:h-9 sm:px-3.5 sm:text-sm',
                  active ? 'bg-primary font-semibold text-primary-foreground shadow-xs' : 'border bg-card text-foreground hover:bg-accent',
                )}
              >
                <Icon className="hidden size-4 lg:block" aria-hidden />
                {TIMEFRAME_LABELS[tf]}
              </button>
            );
          })}
        </div>

        {hasActiveFilters ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={onReset}
            className="h-8 shrink-0 gap-1 px-2 text-xs text-muted-foreground hover:text-foreground sm:h-9 sm:gap-1.5 sm:px-3"
          >
            <RotateCcw className="size-3.5" />
            <span className="hidden sm:inline">Đặt lại bộ lọc</span>
            <span className="sm:hidden">Đặt lại</span>
          </Button>
        ) : null}
      </div>

      {/* Điện thoại: ô tìm hết chiều ngang, 2 dropdown chia đôi; từ md: một hàng. */}
      <div className="flex flex-col gap-2 md:flex-row md:items-center md:gap-2.5">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            type="search"
            aria-label="Tìm kiếm sự kiện"
            className="h-9 rounded-lg bg-card pl-9 text-sm shadow-xs"
            placeholder="Tìm theo tên sự kiện, workshop..."
            maxLength={100}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="grid grid-cols-2 gap-2 md:flex md:shrink-0 md:items-center md:gap-2.5">
          <div className="w-full min-w-0 md:w-48 lg:w-56">
            <Select value={filters.category ?? ALL} onValueChange={(val) => onChange({ category: val === ALL ? undefined : val })}>
              <SelectTrigger
                aria-label="Lọc theo thể loại"
                className={cn(
                  'h-9 w-full min-w-0 rounded-lg bg-card text-xs shadow-xs sm:text-sm',
                  filters.category && 'border-primary font-medium text-primary',
                )}
              >
                <div className="flex min-w-0 items-center gap-2 truncate">
                  <Layers className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  <SelectValue placeholder="Tất cả thể loại" />
                </div>
              </SelectTrigger>
              <SelectContent position="popper">
                <SelectItem value={ALL}>Tất cả thể loại</SelectItem>
                {categories.map((cat) => (
                  <SelectItem key={cat.id} value={cat.slug}>
                    {cat.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="w-full min-w-0 md:w-44 lg:w-48">
            <Select value={filters.time ?? ALL} onValueChange={(val) => onChange({ time: val === ALL ? undefined : (val as TimePreset) })}>
              <SelectTrigger
                aria-label="Lọc theo thời gian"
                className={cn(
                  'h-9 w-full min-w-0 rounded-lg bg-card text-xs shadow-xs sm:text-sm',
                  filters.time && 'border-primary font-medium text-primary',
                )}
              >
                <div className="flex min-w-0 items-center gap-2 truncate">
                  <Calendar className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  <SelectValue placeholder="Tất cả thời gian" />
                </div>
              </SelectTrigger>
              <SelectContent position="popper">
                <SelectItem value={ALL}>Tất cả thời gian</SelectItem>
                {TIME_PRESETS.map((p) => (
                  <SelectItem key={p.key} value={p.key}>
                    {p.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>
    </div>
  );
}
