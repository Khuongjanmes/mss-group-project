import { type ReactNode, useCallback, useEffect, useState } from 'react';

import { type EventAction, EventActionDialog } from '@/admin/EventActionDialog';
import { errorMessage } from '@/api/client';
import type { EventItem, EventStatus } from '@/api/events';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { ListPagination } from '@/components/common/navigation';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EventListTable } from './EventListTable';
import { EventListToolbar } from './EventListToolbar';
import { EventRowActions } from './EventRowActions';

export type EventQuery = { keyword?: string; status?: EventStatus; page: number; size: number };

type EventListPageProps = {
  /** Bỏ trống khi nhúng trong trang khác (tab Sự kiện của tổ chức). */
  title?: string;
  description?: string;
  area: 'organizer' | 'admin';
  load: (query: EventQuery) => Promise<PageResponse<EventItem>>;
  /** Tab lọc nhanh theo trạng thái; bỏ trống thì không có. */
  statuses?: EventStatus[];
  /** Có ô tìm theo tên. */
  searchable?: boolean;
  headerActions?: ReactNode;
  /** Người xem có quyền thao tác trên sự kiện không; mặc định có (sự kiện mình làm HOST). */
  canManage?: (event: EventItem) => boolean;
  emptyTitle: string;
  emptyDescription?: string;
};

/** Trang danh sách sự kiện theo AdminEventsPage của openevent: tiêu đề, rồi thẻ chứa thanh lọc, bảng, phân trang. */
export function EventListPage({
  title,
  description,
  area,
  load,
  statuses,
  searchable,
  headerActions,
  canManage,
  emptyTitle,
  emptyDescription,
}: EventListPageProps) {
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EventStatus>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState<PageResponse<EventItem> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<{ action: EventAction; event: EventItem } | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    load({ keyword: keyword || undefined, status, page, size })
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [load, keyword, status, page, size, reloadKey]);

  const reload = () => setReloadKey((key) => key + 1);
  const changeKeyword = useCallback((value: string) => {
    setKeyword(value);
    setPage(0);
  }, []);
  const filtered = Boolean(keyword) || status !== undefined;

  return (
    <div className="space-y-4 text-sm">
      {(title || headerActions) && (
        <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
          <div>
            {title && <h1 className="text-2xl font-bold tracking-tight text-foreground">{title}</h1>}
            {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
          </div>
          {headerActions && <div className="flex items-center gap-2">{headerActions}</div>}
        </div>
      )}

      <Card className="gap-0 overflow-hidden rounded-xl bg-card py-0 shadow-sm">
        <EventListToolbar
          keyword={searchable ? keyword : undefined}
          onKeywordChange={searchable ? changeKeyword : undefined}
          statuses={statuses}
          status={status}
          onStatusChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
          onRefresh={reload}
          refreshing={loading}
        />

        {loading && !result ? (
          <div className="p-6">
            <LoadingState lines={6} />
          </div>
        ) : error ? (
          <ErrorState description={error} onRetry={reload} />
        ) : result && result.content.length === 0 ? (
          <EmptyState
            title={filtered ? 'Không có sự kiện phù hợp' : emptyTitle}
            description={filtered ? 'Thử từ khóa hoặc bộ lọc khác.' : emptyDescription}
            action={
              filtered ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    changeKeyword('');
                    setStatus(undefined);
                  }}
                >
                  Xóa bộ lọc
                </Button>
              ) : undefined
            }
          />
        ) : (
          result && (
            <>
              <EventListTable
                events={result.content}
                stale={loading}
                detailPath={(event) => `/${area}/events/${event.id}`}
                actions={(event) => (
                  <EventRowActions
                    event={event}
                    area={area}
                    manageable={canManage?.(event) ?? true}
                    onAction={(action, target) => setPending({ action, event: target })}
                  />
                )}
              />
              <ListPagination
                meta={result}
                unit="sự kiện"
                onPageChange={setPage}
                pageSizes={[10, 20, 50]}
                onPageSizeChange={(next) => {
                  setSize(next);
                  setPage(0);
                }}
              />
            </>
          )
        )}
      </Card>

      {pending && (
        <EventActionDialog
          action={pending.action}
          event={pending.event}
          onClose={() => setPending(null)}
          onDone={() => {
            setPending(null);
            reload();
          }}
        />
      )}
    </div>
  );
}
