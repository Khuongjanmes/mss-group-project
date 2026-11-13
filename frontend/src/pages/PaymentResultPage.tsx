import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import { type ReactNode, useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { errorMessage } from '@/api/client';
import { formatMoney, getPayment, type Payment, PAYMENT_STATUS_LABELS } from '@/api/payments';
import { LoadingState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

/** Mã lỗi Payment Service gửi kèm khi VNPay chuyển về mà không xử lý được. */
const RETURN_ERRORS: Record<string, string> = {
  INVALID_SIGNATURE: 'Kết quả thanh toán không hợp lệ nên chưa được ghi nhận.',
  PAYMENT_NOT_FOUND: 'Không tìm thấy giao dịch này.',
  PAYMENT_AMOUNT_MISMATCH: 'Số tiền thanh toán không khớp với đơn hàng.',
};

/** VNPay hoặc cổng giả lập chuyển về đây; trạng thái đọc lại từ server, không tin tham số trên URL. */
export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const paymentId = params.get('paymentId');
  const returnError = params.get('error');
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!paymentId) return;
    setError(null);
    getPayment(paymentId)
      .then(setPayment)
      .catch((err: unknown) => setError(errorMessage(err)));
  }, [paymentId]);

  useEffect(load, [load]);

  if (returnError || !paymentId) {
    return (
      <Result icon={<XCircle className="size-12 text-destructive" />} title="Chưa ghi nhận thanh toán">
        {RETURN_ERRORS[returnError ?? ''] ?? 'Không đọc được kết quả thanh toán.'} Nếu tài khoản đã bị trừ tiền, hãy
        liên hệ ban tổ chức kèm mã đơn hàng.
      </Result>
    );
  }
  if (error) {
    return (
      <Result icon={<XCircle className="size-12 text-destructive" />} title="Không tải được giao dịch">
        {error}
      </Result>
    );
  }
  if (!payment) {
    return (
      <div className="mx-auto max-w-md">
        <LoadingState lines={3} />
      </div>
    );
  }

  const amount = formatMoney(payment.amount);
  switch (payment.status) {
    case 'SUCCESS':
      return (
        <Result icon={<CheckCircle2 className="size-12 text-success" />} title={`Đã thanh toán ${amount}`}>
          Vé sẽ được phát hành và gửi qua email trong ít phút.
        </Result>
      );
    case 'FAILED':
      return (
        <Result icon={<XCircle className="size-12 text-destructive" />} title="Thanh toán không thành công">
          Bạn có thể thử thanh toán lại khi đơn hàng còn hạn giữ vé.
        </Result>
      );
    case 'PENDING':
      return (
        <Result icon={<Clock className="size-12 text-muted-foreground" />} title="Đang chờ kết quả thanh toán">
          <Button variant="outline" size="sm" className="mt-2" onClick={load}>
            Tải lại
          </Button>
        </Result>
      );
    default:
      return (
        <Result icon={<Clock className="size-12 text-muted-foreground" />} title={PAYMENT_STATUS_LABELS[payment.status]}>
          Giao dịch {amount} {payment.refundReason ? `(${payment.refundReason})` : ''}{' '}
          {payment.status === 'REFUNDED' ? 'đã được hoàn lại cho bạn.' : 'sẽ được hoàn lại cho bạn.'}
        </Result>
      );
  }
}

function Result({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <Card className="mx-auto max-w-md">
      <CardContent className="flex flex-col items-center gap-3 text-center">
        {icon}
        <h1 className="text-xl font-bold">{title}</h1>
        <div className="text-sm text-muted-foreground">{children}</div>
        <Link to="/events" className="text-sm font-medium text-accent-foreground hover:underline">
          Xem sự kiện
        </Link>
      </CardContent>
    </Card>
  );
}
