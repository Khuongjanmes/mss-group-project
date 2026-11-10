import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { completeMockPayment, formatMoney, getPayment, type Payment, PAYMENT_STATUS_LABELS } from '@/api/payments';
import { ErrorState, LoadingState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Spinner } from '@/components/ui/spinner';

/** Cổng giả lập khi chưa cấu hình VNPay: người mua tự chọn kết quả để chạy được luồng đặt vé. */
export default function MockPaymentPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<boolean | null>(null);

  useEffect(() => {
    let ignore = false;
    getPayment(id)
      .then((data) => !ignore && setPayment(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [id]);

  async function finish(success: boolean) {
    setSubmitting(success);
    setError(null);
    try {
      await completeMockPayment(id, success);
      navigate(`/payments/result?paymentId=${id}`, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(null);
    }
  }

  if (!payment) {
    return (
      <div className="mx-auto max-w-md">{error ? <ErrorState description={error} /> : <LoadingState lines={4} />}</div>
    );
  }

  return (
    <Card className="mx-auto max-w-md">
      <CardContent className="space-y-5 text-center">
        <p className="text-xs font-bold tracking-wider text-muted-foreground uppercase">Cổng thanh toán giả lập</p>
        <p className="text-3xl font-bold tabular-nums">{formatMoney(payment.amount)}</p>
        <p className="text-sm text-muted-foreground">
          Đơn hàng <code>{payment.orderId.slice(0, 8)}</code>. Đây là môi trường thử, không trừ tiền thật.
        </p>
        {payment.status !== 'PENDING' ? (
          <p className="text-sm">
            Giao dịch đã ở trạng thái <strong>{PAYMENT_STATUS_LABELS[payment.status]}</strong>.{' '}
            <Link to={`/payments/result?paymentId=${id}`} className="font-medium text-accent-foreground hover:underline">
              Xem kết quả
            </Link>
          </p>
        ) : (
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-center">
            <Button disabled={submitting !== null} onClick={() => void finish(true)}>
              {submitting === true && <Spinner />}
              Thanh toán thành công
            </Button>
            <Button variant="outline" disabled={submitting !== null} onClick={() => void finish(false)}>
              {submitting === false && <Spinner />}
              Thanh toán thất bại
            </Button>
          </div>
        )}
        {error && (
          <p role="alert" className="text-sm font-medium text-destructive">
            {error}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
