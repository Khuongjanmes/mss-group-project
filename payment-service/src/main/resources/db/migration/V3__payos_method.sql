-- payOS: chuyển khoản VietQR vào tài khoản ngân hàng đã liên kết ở my.payos.vn.
ALTER TABLE payments DROP CONSTRAINT payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_method_check CHECK (method IN ('VNPAY', 'MOCK', 'PAYOS'));
