import { Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { type Category, type EventItem, listCategories, listVenues, searchEvents } from '@/api/events';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState } from '@/components/common/feedback';
import { ListPagination } from '@/components/common/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CategoryChips } from '@/events/CategoryChips';
import { EventCardGrid, EventCardGridSkeleton } from '@/events/EventCard';
import { parseTimeRange, TIME_RANGES, timeRange } from '@/events/eventTimeRange';
import { formatNumber } from '@/lib/pagination';

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;
/** Radix Select không cho giá trị rỗng nên "tất cả" dùng giá trị này. */
const ALL = 'all';

/**
 * Trang tìm sự kiện PUBLISHED theo EventSearchPage của openevent SBA301. Bộ lọc nằm trên URL
 * (`?keyword&category&city&when&page`) để tải lại hoặc chia sẻ link vẫn giữ nguyên.
 */
export default function EventsPage() {
  const [params, setParams] = useSearchParams();
  const keyword = params.get('keyword')?.trim() ?? '';
  const category = params.get('category') ?? '';
  const city = params.get('city') ?? '';
  const when = parseTimeRange(params.get('when'));
  // URL đếm trang từ 1, API đếm từ 0.
  const page = Math.max(0, (Number(params.get('page')) || 1) - 1);

  const [search, setSearch] = useState(keyword);
  const [categories, setCategories] = useState<Category[]>([]);
  const [cities, setCities] = useState<string[]>([]);
  const [result, setResult] = useState<PageResponse<EventItem> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  /** Đổi bộ lọc thì về trang 1; giá trị rỗng thì bỏ khỏi URL. */
  const update = (changes: Record<string, string | undefined>, options: { replace?: boolean } = {}) => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        if (!('page' in changes)) next.delete('page');
        return next;
      },
      { replace: options.replace },
    );
  };

  // Danh mục và thành phố cho bộ lọc; lỗi thì chỉ ẩn bộ lọc đó.
  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
    listVenues()
      .then((venues) => setCities([...new Set(venues.map((v) => v.city.trim()))].sort((a, b) => a.localeCompare(b, 'vi'))))
      .catch(() => setCities([]));
  }, []);

  // Bấm "Xóa bộ lọc" hoặc quay lại trang trước thì ô tìm cập nhật theo URL.
  useEffect(() => {
    setSearch((current) => (current.trim() === keyword ? current : keyword));
  }, [keyword]);

  // Gõ xong 300ms mới tìm; thay URL hiện tại thay vì thêm một mục lịch sử cho mỗi lần gõ.
  useEffect(() => {
    if (search.trim() === keyword) return;
    const timer = setTimeout(() => update({ keyword: search.trim() }, { replace: true }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // Không đưa update vào deps: hàm đổi mỗi lần render, còn URL hiện tại đã được đọc qua callback của setParams.
  }, [search, keyword]);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    searchEvents({
      keyword: keyword || undefined,
      category: category || undefined,
      city: city || undefined,
      ...timeRange(when),
      page,
      size: PAGE_SIZE,
    })
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [keyword, category, city, when, page, reloadKey]);

  const filtered = Boolean(keyword || category || city || when);
  const clearFilters = () => {
    setSearch('');
    setParams({});
  };

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight md:text-[28px]">Khám phá sự kiện</h1>
        <p className="text-sm text-muted-foreground">Tìm theo tên sự kiện, lọc theo danh mục, thành phố và thời gian.</p>
      </header>

      <section aria-label="Bộ lọc" className="space-y-4 rounded-xl border bg-card p-4 shadow-sm">
        <div className="flex flex-col gap-3 md:flex-row">
          <div className="relative flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              aria-label="Tìm sự kiện"
              placeholder="Tìm theo tên sự kiện..."
              maxLength={100}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-10 rounded-lg bg-surface-alt pl-9 shadow-none focus-visible:bg-card"
            />
          </div>
          <div className="grid grid-cols-2 gap-3 md:flex">
            {cities.length > 0 && (
              <Select value={city || ALL} onValueChange={(value) => update({ city: value === ALL ? undefined : value })}>
                <SelectTrigger aria-label="Thành phố" className="h-10! w-full rounded-lg md:w-44">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent position="popper">
                  <SelectItem value={ALL}>Mọi thành phố</SelectItem>
                  {cities.map((name) => (
                    <SelectItem key={name} value={name}>
                      {name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
            <Select value={when ?? ALL} onValueChange={(value) => update({ when: value === ALL ? undefined : value })}>
              <SelectTrigger aria-label="Thời gian" className="h-10! w-full rounded-lg md:w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent position="popper">
                <SelectItem value={ALL}>Mọi thời gian</SelectItem>
                {TIME_RANGES.map((range) => (
                  <SelectItem key={range.key} value={range.key}>
                    {range.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        {categories.length > 0 && (
          <CategoryChips categories={categories} value={category} onChange={(slug) => update({ category: slug })} />
        )}
      </section>

      {loading && !result ? (
        <EventCardGridSkeleton />
      ) : error ? (
        <ErrorState description={error} onRetry={() => setReloadKey((key) => key + 1)} />
      ) : result && result.content.length === 0 ? (
        <EmptyState
          title={filtered ? 'Không có sự kiện phù hợp' : 'Chưa có sự kiện nào được công bố'}
          description={filtered ? 'Thử từ khóa hoặc bộ lọc khác.' : 'Quay lại sau nhé.'}
          action={
            filtered ? (
              <Button variant="outline" onClick={clearFilters}>
                Xóa bộ lọc
              </Button>
            ) : undefined
          }
        />
      ) : (
        result && (
          <section aria-label="Kết quả" className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm text-muted-foreground" aria-live="polite">
                Tìm thấy <span className="font-semibold text-foreground">{formatNumber(result.totalElements)}</span> sự kiện
              </p>
              {filtered && (
                <Button variant="ghost" size="sm" className="text-muted-foreground" onClick={clearFilters}>
                  <X className="size-4" />
                  Xóa bộ lọc
                </Button>
              )}
            </div>
            <EventCardGrid events={result.content} stale={loading} />
            {result.totalPages > 1 && (
              <ListPagination
                meta={result}
                unit="sự kiện"
                onPageChange={(next) => {
                  update({ page: next > 0 ? String(next + 1) : undefined });
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
              />
            )}
          </section>
        )
      )}
    </div>
  );
}
