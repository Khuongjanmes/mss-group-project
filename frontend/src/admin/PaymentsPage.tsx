import { useEffect, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/api/client';
import {
  formatMoney,
  markPaymentRefunded,
  type Payment,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_BADGE,
  PAYMENT_STATUS_LABELS,
  type PaymentStatus,
  refundReasonLabel,
  searchPayments,
} from '@/api/payments';
import type { PageResponse } from '@/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { ListPagination } from '@/components/common/navigation';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime } from '@/lib/datetime';
import { cn } from '@/lib/utils';

const ALL = 'ALL';

/** Admin #4: mọi giao dịch, lọc theo trạng thái; đánh dấu đã hoàn tiền sau khi hoàn thủ công qua cổng thanh toán. */
export default function PaymentsPage() {
  const [status, setStatus] = useState<PaymentStatus | typeof ALL>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [result, setResult] = useState<PageResponse<Payment> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [refunding, setRefunding] = useState<Payment | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    searchPayments({ status: status === ALL ? undefined : status, page, size })
      .then((data) => !ignore && setResult(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [status, page, size, reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);

  return (
    <div className="space-y-6 text-sm">
      <PageHeader
        title="Giao dịch thanh toán"
        description="Giao dịch chờ hoàn tiền xuất hiện khi sự kiện bị hủy hoặc người mua thanh toán sau khi đơn đã hết hạn. Hoàn tiền qua cổng thanh toán rồi đánh dấu ở đây."
      />

      <Select
        value={status}
        onValueChange={(value) => {
          setStatus(value as PaymentStatus | typeof ALL);
          setPage(0);
        }}
      >
        <SelectTrigger className="w-56 bg-background" aria-label="Lọc theo trạng thái">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>Tất cả trạng thái</SelectItem>
          {(Object.keys(PAYMENT_STATUS_LABELS) as PaymentStatus[]).map((value) => (
            <SelectItem key={value} value={value}>
              {PAYMENT_STATUS_LABELS[value]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Card className="py-0">
        <CardContent className="px-0">
          {error ? (
            <ErrorState description={error} onRetry={reload} />
          ) : !result ? (
            <div className="p-6">
              <LoadingState lines={5} />
            </div>
          ) : result.content.length === 0 ? (
            <EmptyState
              title={status === ALL ? 'Chưa có giao dịch nào' : 'Không có giao dịch ở trạng thái này'}
              description="Giao dịch được tạo khi người mua bấm thanh toán đơn hàng."
            />
          ) : (
            <>
              <Table aria-busy={loading} className={cn(loading && 'opacity-60')}>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">Mã giao dịch</TableHead>
                    <TableHead>Đơn hàng</TableHead>
                    <TableHead className="text-right">Số tiền</TableHead>
                    <TableHead>Cổng</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead>Thời gian</TableHead>
                    <TableHead className="pr-6 text-right">
                      <span className="sr-only">Thao tác</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((payment) => (
                    <TableRow key={payment.id}>
                      <TableCell className="pl-6">
                        <code className="text-xs" title={payment.transactionRef}>
                          {payment.transactionRef.slice(0, 12)}…
                        </code>
                      </TableCell>
                      <TableCell>
                        <code className="text-xs text-muted-foreground" title={payment.orderId}>
                          {payment.orderId.slice(0, 8)}
                        </code>
                      </TableCell>
                      <TableCell className="text-right font-medium tabular-nums">{formatMoney(payment.amount)}</TableCell>
                      <TableCell>{PAYMENT_METHOD_LABELS[payment.method]}</TableCell>
                      <TableCell>
                        <Badge variant={PAYMENT_STATUS_BADGE[payment.status]}>{PAYMENT_STATUS_LABELS[payment.status]}</Badge>
                        {payment.refundReason && (
                          <p className="mt-1 max-w-56 truncate text-xs text-muted-foreground" title={payment.refundReason}>
                            {refundReasonLabel(payment.refundReason)}
                          </p>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDateTime(payment.paidAt ?? payment.createdAt)}
                      </TableCell>
                      <TableCell className="pr-6 text-right">
                        {payment.status === 'REFUND_PENDING' && (
                          <Button size="sm" variant="outline" onClick={() => setRefunding(payment)}>
                            Đã hoàn tiền
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <ListPagination
                meta={result}
                unit="giao dịch"
                onPageChange={setPage}
                pageSizes={[10, 20, 50]}
                onPageSizeChange={(next) => {
                  setSize(next);
                  setPage(0);
                }}
              />
            </>
          )}
        </CardContent>
      </Card>

      {refunding && (
        <MarkRefundedDialog
          payment={refunding}
          onClose={() => setRefunding(null)}
          onDone={() => {
            setRefunding(null);
            reload();
          }}
        />
      )}
    </div>
  );
}

function MarkRefundedDialog({ payment, onClose, onDone }: { payment: Payment; onClose: () => void; onDone: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <AlertDialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Đánh dấu đã hoàn {formatMoney(payment.amount)}?</AlertDialogTitle>
          <AlertDialogDescription>
            Chỉ đánh dấu sau khi đã hoàn tiền cho người mua qua {PAYMENT_METHOD_LABELS[payment.method]}. Không hoàn
            tác được.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <p role="alert" className="text-sm font-medium text-destructive">
            {error}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Quay lại</AlertDialogCancel>
          <AlertDialogAction
            disabled={pending}
            onClick={(event) => {
              event.preventDefault();
              setPending(true);
              setError(null);
              markPaymentRefunded(payment.id)
                .then(() => {
                  toast.success(`Đã đánh dấu hoàn ${formatMoney(payment.amount)}`);
                  onDone();
                })
                .catch((err: unknown) => setError(errorMessage(err)))
                .finally(() => setPending(false));
            }}
          >
            Đã hoàn tiền
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
