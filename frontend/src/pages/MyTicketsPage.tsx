import { CalendarClock, History, LayoutList, ListFilter, Radio, Search, type LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { type EventItem, getEvent } from '@/api/events';
import { myTickets, type Ticket, TICKET_STATUS_LABELS, type TicketStatus } from '@/api/tickets';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { cn } from '@/lib/utils';
import { emptyStateText, groupByEvent, inTimeframe, type Timeframe } from '@/tickets/myTicketsDisplay';
import { TicketEventCard } from '@/tickets/TicketEventCard';

const ALL = 'ALL';
const SEARCH_DEBOUNCE_MS = 300;
const STATUSES: TicketStatus[] = ['ISSUED', 'CHECKED_IN', 'CANCELLED'];

const TIMEFRAME_TABS: { label: string; value: Timeframe; icon: LucideIcon }[] = [
  { label: 'Tất cả', value: 'all', icon: LayoutList },
  { label: 'Sắp tới', value: 'upcoming', icon: CalendarClock },
  { label: 'Đang diễn ra', value: 'ongoing', icon: Radio },
  { label: 'Đã kết thúc', value: 'ended', icon: History },
];

/**
 * Vé của tôi theo MyEventsPage của openevent: tab thời gian, tìm theo tên sự kiện, lọc trạng thái vé; mỗi sự kiện
 * một thẻ, bấm "Mã QR vé" để mở mã cho người soát vé. Bộ lọc nằm trên URL (`?timeframe&keyword&status`).
 */
export default function MyTicketsPage() {
  const [params, setParams] = useSearchParams();
  const timeframe = (TIMEFRAME_TABS.find((t) => t.value === params.get('timeframe'))?.value ?? 'all') as Timeframe;
  const keyword = params.get('keyword')?.trim() ?? '';
  const status = STATUSES.find((s) => s === params.get('status'));

  const [tickets, setTickets] = useState<Ticket[]>();
  const [events, setEvents] = useState(new Map<string, EventItem>());
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [search, setSearch] = useState(keyword);

  const update = useCallback(
    (changes: Record<string, string | undefined>) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [key, value] of Object.entries(changes)) {
            if (!value || value === 'all') next.delete(key);
            else next.set(key, value);
          }
          return next;
        },
        { replace: 'keyword' in changes },
      ),
    [setParams],
  );

  // Vé và sự kiện của chúng; sự kiện không tải được thì thẻ vẫn hiện, thiếu tên và ảnh.
  useEffect(() => {
    let ignore = false;
    setError(undefined);
    myTickets()
      .then(async (data) => {
        const ids = [...new Set(data.map((t) => t.eventId).filter((id): id is string => Boolean(id)))];
        const loaded = await Promise.allSettled(ids.map((id) => getEvent(id)));
        if (ignore) return;
        setEvents(new Map(loaded.flatMap((r) => (r.status === 'fulfilled' ? [[r.value.id, r.value] as const] : []))));
        setTickets(data);
      })
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  // Hộp mã QR đang mở hỏi lại vé để thấy check-in; chỉ tải vé, không tải lại sự kiện.
  const poll = useCallback(() => {
    myTickets().then(setTickets).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (search.trim() === keyword) return;
    const timer = setTimeout(() => update({ keyword: search.trim() }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search, keyword, update]);

  const groups = useMemo(() => {
    if (!tickets) return [];
    const needle = keyword.toLocaleLowerCase('vi');
    return groupByEvent(tickets, events).filter(
      (g) =>
        inTimeframe(g, timeframe) &&
        (!status || g.tickets.some((t) => t.status === status)) &&
        (!needle || (g.event?.title ?? '').toLocaleLowerCase('vi').includes(needle)),
    );
  }, [tickets, events, timeframe, status, keyword]);

  const filtered = Boolean(keyword || status);
  const empty = emptyStateText(filtered, timeframe);

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-6.5rem)] flex-col gap-4 lg:min-h-[calc(100dvh-8rem)]">
      <div>
        <PageHeader title="Vé của tôi" description="Vé bạn đã mua; mở mã QR để check-in khi đến sự kiện." />
        {/* Điện thoại: 4 tab chia đều (2 × 2), rồi ô tìm kèm bộ lọc trạng thái; từ lg: một hàng. */}
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:flex lg:shrink-0" role="group" aria-label="Lọc theo thời gian">
            {TIMEFRAME_TABS.map(({ label, value, icon: Icon }) => {
              const active = timeframe === value;
              return (
                <button
                  key={value}
                  type="button"
                  aria-pressed={active}
                  onClick={() => update({ timeframe: value })}
                  className={cn(
                    'inline-flex h-9 items-center justify-center gap-1.5 rounded-full px-2 text-[13px] whitespace-nowrap transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 lg:px-4 lg:text-sm',
                    active ? 'bg-primary font-semibold text-primary-foreground shadow-sm' : 'border bg-card font-medium text-foreground hover:bg-accent',
                  )}
                >
                  <Icon className="hidden size-4 lg:block" aria-hidden />
                  {label}
                </button>
              );
            })}
          </div>

          <div className="flex min-w-0 flex-1 gap-2 lg:gap-3">
            <div className="relative min-w-0 flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input
                type="search"
                aria-label="Tìm sự kiện"
                className="h-9 rounded-lg bg-card pl-9 shadow-xs"
                placeholder="Tìm theo tên sự kiện..."
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            <Select value={status ?? ALL} onValueChange={(value) => update({ status: value === ALL ? undefined : value })}>
              <SelectTrigger
                aria-label="Lọc theo trạng thái vé"
                className={cn('h-9 w-auto shrink-0 rounded-lg bg-card shadow-xs sm:w-48', status && 'border-primary font-medium text-primary')}
              >
                <div className="flex min-w-0 items-center gap-2 truncate">
                  <ListFilter className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="hidden sm:inline">
                    <SelectValue />
                  </span>
                </div>
              </SelectTrigger>
              <SelectContent position="popper" align="end">
                <SelectItem value={ALL}>Mọi trạng thái vé</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {TICKET_STATUS_LABELS[s]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <section aria-label="Vé của tôi theo sự kiện" className={cn('flex flex-1 flex-col', (error || (tickets && groups.length === 0)) && 'justify-center')}>
        {error ? (
          <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />
        ) : !tickets ? (
          <LoadingState lines={4} />
        ) : groups.length === 0 ? (
          <EmptyState
            title={empty.title}
            description={empty.description}
            action={
              filtered ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setSearch('');
                    update({ keyword: undefined, status: undefined });
                  }}
                >
                  Xoá bộ lọc
                </Button>
              ) : (
                <Button asChild variant="outline">
                  <Link to="/events">Xem danh sách sự kiện</Link>
                </Button>
              )
            }
          />
        ) : (
          <ul className="space-y-3">
            {groups.map((group) => (
              <li key={group.eventId}>
                <TicketEventCard group={group} onPoll={poll} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
