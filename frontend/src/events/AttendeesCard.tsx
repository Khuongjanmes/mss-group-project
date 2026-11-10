import { Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { errorMessage } from '@/api/client';
import { type Attendee, listAttendees, TICKET_STATUS_BADGE, TICKET_STATUS_LABELS } from '@/api/tickets';
import type { PageResponse } from '@/api/types';
import { ListPagination } from '@/components/common/navigation';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime } from '@/lib/datetime';

const TITLE = 'flex items-center gap-2 text-[11px] font-bold tracking-wider text-foreground uppercase';

/** Danh sách người tham dự (người quản lý sự kiện); họ tên và email lấy từ Identity. */
export function AttendeesCard({ eventId }: { eventId: string }) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<Attendee> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    listAttendees(eventId, page, 20)
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [eventId, page]);

  return (
    <Card id="attendees" className="scroll-mt-20 gap-0 rounded-xl py-0 shadow-sm">
      <CardContent className="space-y-4 px-0 py-5">
        <div className="flex items-center justify-between gap-3 px-5">
          <h2 className={TITLE}>
            <Users className="size-4 text-accent-foreground" aria-hidden />
            Người tham dự {result && `(${result.totalElements})`}
          </h2>
          <Button asChild size="sm" variant="outline">
            <Link to="/organizer/check-in">Mở trang check-in</Link>
          </Button>
        </div>
        {error ? (
          <p role="alert" className="px-5 text-sm text-destructive">
            {error}
          </p>
        ) : !result ? (
          <p className="px-5 text-sm text-muted-foreground">Đang tải…</p>
        ) : result.content.length === 0 ? (
          <p className="px-5 text-sm text-muted-foreground">Chưa có vé nào được phát hành.</p>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-5">Người mua</TableHead>
                  <TableHead>Loại vé</TableHead>
                  <TableHead>Mã vé</TableHead>
                  <TableHead className="pr-5">Trạng thái</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {result.content.map((attendee) => (
                  <TableRow key={attendee.ticketId}>
                    <TableCell className="pl-5">
                      <p className="font-medium">{attendee.ownerName ?? 'Người dùng'}</p>
                      {attendee.ownerEmail && <p className="text-xs text-muted-foreground">{attendee.ownerEmail}</p>}
                    </TableCell>
                    <TableCell>{attendee.ticketTypeName ?? '—'}</TableCell>
                    <TableCell>
                      <code className="text-xs text-muted-foreground">{attendee.code.slice(0, 8)}…</code>
                    </TableCell>
                    <TableCell className="pr-5">
                      <Badge variant={TICKET_STATUS_BADGE[attendee.status]}>{TICKET_STATUS_LABELS[attendee.status]}</Badge>
                      {attendee.checkedInAt && (
                        <p className="mt-1 text-xs text-muted-foreground">{formatDateTime(attendee.checkedInAt)}</p>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <ListPagination meta={result} unit="vé" onPageChange={setPage} />
          </>
        )}
      </CardContent>
    </Card>
  );
}
