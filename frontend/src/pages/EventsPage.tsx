import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { type Category, type EventItem, type EventTimeline, listCategories, searchEvents } from '@/api/events';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { EventRowCard } from '@/events/EventRowCard';
import { EVENT_TIMEFRAMES, type EventTimeframe, EventsFilters, type EventsFilterValues } from '@/events/EventsFilters';
import { parseTimePreset, timeRange } from '@/events/eventTimeRange';
import { cn } from '@/lib/utils';

const PAGE_SIZE = 20;

const TIMEFRAME_TIMELINES: Record<EventTimeframe, EventTimeline | undefined> = {
  all: undefined,
  upcoming: 'UPCOMING',
  ongoing: 'ONGOING',
  ended: 'ENDED',
};

/**
 * Trang sự kiện theo EventsPage của openevent. Bộ lọc nằm trên URL (`?keyword&timeframe&category&time&page`) để tải
 * lại hoặc chia sẻ link vẫn giữ nguyên; `page` đếm từ 1.
 */
export default function EventsPage() {
  const [params, setParams] = useSearchParams();
  const timeframeParam = params.get('timeframe') as EventTimeframe | null;
  const filters: EventsFilterValues = useMemo(
    () => ({
      keyword: params.get('keyword')?.trim() ?? '',
      timeframe: timeframeParam && EVENT_TIMEFRAMES.includes(timeframeParam) ? timeframeParam : 'all',
      category: params.get('category') || undefined,
      time: parseTimePreset(params.get('time')),
    }),
    [params, timeframeParam],
  );
  const page = Math.max(0, (Number(params.get('page')) || 1) - 1);

  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<PageResponse<EventItem>>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  /** Đổi bộ lọc thì về trang 1; giá trị rỗng hoặc mặc định thì bỏ khỏi URL. */
  const update = useCallback(
    (changes: Partial<EventsFilterValues> & { page?: number }) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [key, value] of Object.entries(changes)) {
            const empty = value === undefined || value === '' || (key === 'timeframe' && value === 'all');
            if (empty) next.delete(key);
            else next.set(key, key === 'page' ? String(Number(value) + 1) : String(value));
          }
          if (!('page' in changes)) next.delete('page');
          return next;
        },
        { replace: 'keyword' in changes },
      );
    },
    [setParams],
  );
  const reset = useCallback(() => setParams({}), [setParams]);

  useEffect(() => {
    listCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(undefined);
    searchEvents({
      keyword: filters.keyword || undefined,
      category: filters.category,
      timeline: TIMEFRAME_TIMELINES[filters.timeframe],
      ...timeRange(filters.time),
      page,
      size: PAGE_SIZE,
    })
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [filters, page, reloadKey]);

  const events = result?.content ?? [];
  const hasFilters = filters.keyword !== '' || filters.category !== undefined || filters.timeframe !== 'all' || filters.time !== undefined;

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-6.5rem)] flex-col gap-4 lg:min-h-[calc(100dvh-8rem)]">
      <div className="space-y-4">
        <PageHeader title="Sự kiện" description="Khám phá và mua vé các sự kiện, workshop và hoạt động sinh viên." />
        <EventsFilters filters={filters} onChange={update} onReset={reset} categories={categories} />
      </div>

      <section
        aria-label="Danh sách sự kiện"
        className={cn('flex flex-1 flex-col', (error || (result && events.length === 0)) && 'justify-center')}
      >
        {loading && !result ? (
          <LoadingState lines={4} />
        ) : error ? (
          <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />
        ) : events.length === 0 ? (
          <EmptyState
            title="Không tìm thấy sự kiện phù hợp"
            description={
              hasFilters ? 'Thử thay đổi hoặc đặt lại bộ lọc để xem các sự kiện khác.' : 'Hiện tại chưa có sự kiện nào được công bố.'
            }
            action={
              hasFilters ? (
                <Button variant="outline" onClick={reset}>
                  Xoá bộ lọc
                </Button>
              ) : undefined
            }
          />
        ) : (
          <ul className={cn('space-y-3', loading && 'opacity-60')}>
            {events.map((event) => (
              <li key={event.id}>
                <EventRowCard event={event} />
              </li>
            ))}
          </ul>
        )}
      </section>

      {result && result.totalPages > 1 ? (
        <nav className="flex items-center justify-between gap-4 pt-2" aria-label="Phân trang sự kiện">
          <p className="text-sm text-muted-foreground">
            Trang {result.page + 1}/{result.totalPages} ({result.totalElements} sự kiện)
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={result.page === 0} onClick={() => update({ page: result.page - 1 })}>
              <ChevronLeft className="size-4" />
              Trước
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={result.page + 1 >= result.totalPages}
              onClick={() => update({ page: result.page + 1 })}
            >
              Sau
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </nav>
      ) : null}
    </div>
  );
}
