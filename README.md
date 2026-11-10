# OpenEvent

Nền tảng tổ chức và bán vé sự kiện cho môn MSS301. Yêu cầu và thiết kế nằm trong [requirement.md](requirement.md).

## Cấu trúc và phân công

| Thư mục | Cổng | Database | Phụ trách (TV, tài khoản GitHub) | Trạng thái |
| --- | --- | --- | --- | --- |
| [eureka-server/](eureka-server/) | 8761 | — | TV1 @YuiGuende | Hoàn thành |
| [api-gateway/](api-gateway/) | 8080 | — | TV1 @YuiGuende | Hoàn thành |
| [identity-service/](identity-service/) | 8081 | PostgreSQL `identity_db` | TV1 @YuiGuende | Hoàn thành |
| [notification-service/](notification-service/) | 8086 | MongoDB `notification_db` | TV1 @YuiGuende | Khung |
| [event-service/](event-service/) | 8082 | PostgreSQL `event_db` | TV2 @duev-dev | Khung |
| [payment-service/](payment-service/) | 8085 | PostgreSQL `payment_db` | TV2 @duev-dev | Khung |
| [ticket-service/](ticket-service/) | 8083 | PostgreSQL `ticket_db` | TV3 @TranQuan1105 | Khung |
| [order-service/](order-service/) | 8084 | PostgreSQL `order_db` | TV4 @Khuongjanmes | Khung |
| [common-lib/](common-lib/) | — | — | Cả nhóm | Định dạng lỗi, danh tính, sự kiện RabbitMQ |

Một service "khung" đã có: schema Flyway theo requirement, kết nối database và RabbitMQ, đăng ký Eureka, bảo mật từ `common-lib`, Swagger, queue và listener rỗng, Feign client, test tích hợp. Phần còn lại là entity, nghiệp vụ và API thật. Tìm `TODO` trong service của bạn để bắt đầu.

## Cần cài

- JDK 21
- Docker Desktop (đang chạy). Test tích hợp `*IT` và `docker compose` đều cần Docker.
- Không cần cài Maven: dùng `./mvnw` (Windows: `mvnw.cmd`).

## Chạy toàn bộ bằng Docker

```bash
cp .env.example .env       # rồi điền JWT_SECRET và BOOTSTRAP_ADMIN_PASSWORD
docker compose up --build
```

| Địa chỉ | Dùng để |
| --- | --- |
| http://localhost:8080 | API Gateway, cổng duy nhất cho client |
| http://localhost:8761 | Eureka dashboard |
| http://localhost:15672 | RabbitMQ (tài khoản trong `.env`) |

Thử nhanh:

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"a@example.com","password":"Passw0rd!","fullName":"Nguyen Van A"}'
```

## Phát triển một service trên máy

Chạy hạ tầng bằng Docker rồi chạy service đang làm từ IDE hoặc Maven.

```bash
docker compose up -d postgres mongodb rabbitmq eureka-server
./mvnw -pl common-lib -am install -DskipTests           # chạy lại khi common-lib thay đổi

./mvnw -pl api-gateway spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl identity-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl event-service spring-boot:run
```

Giá trị mặc định khi chạy trên máy dùng mật khẩu `openevent` cho PostgreSQL và RabbitMQ, khớp với `.env.example`. Profile `local` có sẵn JWT secret chỉ dành cho máy cá nhân và admin `admin@openevent.local` / `Admin@12345`.

Swagger của service chạy trên máy: `http://localhost:<cổng>/swagger-ui.html`.

## Frontend

[frontend/](frontend/) là React 18 + TypeScript + Vite, React Router, Axios, TailwindCSS. Cần Node 20 trở lên.

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173, gọi API qua Gateway ở VITE_API_URL (mặc định http://localhost:8080)
npm run build          # kiểm tra kiểu (tsc) rồi build
```

- Mỗi service một file trong `src/api/`, khai báo kiểu và hàm gọi API, ví dụ [src/api/events.ts](frontend/src/api/events.ts). Dùng `api` trong [src/api/client.ts](frontend/src/api/client.ts), không tạo axios riêng. Lỗi lấy bằng `errorMessage(error)`.
- Kiểu dùng chung (`PageResponse`, `ApiError`) ở [src/api/types.ts](frontend/src/api/types.ts).
- Thêm trang vào `src/pages/` và route vào [src/router.tsx](frontend/src/router.tsx).
- Component giao diện là shadcn/ui chép từ repo openevent: `src/components/ui/` (nút, bảng, dialog…) và `src/components/common/` (khung trang quản trị, phân trang, trạng thái tải/lỗi/rỗng). Import bằng alias `@/`, ví dụ `import { Button } from '@/components/ui/button'`.
- Khu quản trị `/admin` (chỉ ADMIN) nằm trong `src/admin/`: thêm mục menu ở [adminNav.ts](frontend/src/admin/adminNav.ts) và route con có `handle: { crumb }` trong `router.tsx`.
- Người đang đăng nhập: `useSession()` và `hasRole()` trong [src/auth/session.ts](frontend/src/auth/session.ts); chặn route theo vai trò bằng `<RequireRole role="…" />`. Đăng nhập hiện chỉ có email + mật khẩu và chưa tự refresh token: hết 15 phút phải đăng nhập lại.
- Màu, font, bo góc lấy theo token trong [src/index.css](frontend/src/index.css) (`bg-primary`, `text-muted-foreground`, `bg-success/12 text-success`…), không dùng mã màu hay màu thô của Tailwind. Quy tắc tương phản ghi ở đầu file.
- Đổi địa chỉ Gateway: chép `.env.example` thành `.env.local`.

## Kiểm thử

```bash
./mvnw test                       # unit test, không cần Docker
./mvnw verify                     # thêm test tích hợp *IT, cần Docker
./mvnw -pl order-service -am verify     # một service
```

## Quy trình làm việc trên GitHub

Mỗi việc đi theo vòng: **issue → nhánh → commit → PR → review → merge**. Không push thẳng `main` hay `develop`. Hướng dẫn từng bước, lệnh, checklist, release và cấu hình repo: [CONTRIBUTING.md](CONTRIBUTING.md). Người phụ trách từng service: bảng [Cấu trúc và phân công](#cấu-trúc-và-phân-công).

1. **Issue trước khi code.** Tạo bằng mẫu *Chức năng* hoặc *Lỗi*, có tiêu chí chấp nhận, người nhận (Assignees), label (`type:`, `module:`, `priority:`), milestone theo checkpoint (`CP1`–`CP4`) và gắn vào Project board.
2. **Nhánh** tạo từ `develop`, tên `feature/<số issue>-<service>-<tính năng>`, ví dụ `feature/8-ticket-check-in`. Việc cấu hình dùng `chore/`, sửa lỗi dùng `fix/`.
3. **Commit** nhỏ, mỗi commit build được, theo Conventional Commits và có `Refs #<số issue>` ở dòng mô tả:
   ```bash
   git commit -m "feat(ticket): check-in bằng mã vé" -m "Refs #8"
   ```
   Dùng email trùng với tài khoản GitHub để commit được tính cho đúng người.
4. **Pull request** vào `develop` theo mẫu: dòng đầu `Closes #<số issue>`, mục *Cách kiểm thử* ghi lệnh đã chạy. CODEOWNERS tự mời người phụ trách service review.
5. **Review:** ít nhất một người **khác người viết** approve. Bị yêu cầu sửa thì thêm commit mới, không force-push sau khi đã có review.
6. **Merge:** tác giả PR bấm *Squash and merge* khi đã được approve và test qua; **giữ nhánh**, không xóa sau khi merge. Issue chuyển sang Done trên board.

## Quy ước khi viết service

**Lấy người gọi hiện tại.** Gateway xác minh JWT rồi truyền `X-User-Id`, `X-User-Email`, `X-User-Roles`. `common-lib` đã chuyển chúng thành principal:

```java
@PostMapping("/api/orders")
public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateOrderRequest request) {
    // user.id(), user.email(), user.hasRole("ADMIN")
}
```

**Phân quyền ở hai lớp.** API mặc định yêu cầu đăng nhập.

- Theo vai trò: `@PreAuthorize("hasRole('ADMIN')")` trên controller, đồng thời thêm rule trong [GatewaySecurityConfig.java](api-gateway/src/main/java/com/openevent/gateway/security/GatewaySecurityConfig.java).
- Theo dữ liệu, ví dụ "chỉ organizer của sự kiện này" (BR-06): kiểm tra trong service.
- API công khai mới: khai báo ở cả `openevent.security.public-paths` của service và `GatewaySecurityConfig`.
- Đường dẫn API mới có tiền tố mới: thêm route trong [application.yml của Gateway](api-gateway/src/main/resources/application.yml).

**API cho service khác gọi** đặt dưới `/internal/**`. Đường này không cần đăng nhập ở service, Gateway chặn từ bên ngoài. Vì vậy không mở cổng service ra ngoài trong `docker-compose.yml`.

**Báo lỗi nghiệp vụ** bằng `ApiException`; mọi lỗi đều trả về cùng định dạng JSON:

```java
throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Loại vé này đã hết");
```

**Thay đổi schema** bằng file Flyway mới (`V2__mo_ta.sql`), không sửa file đã chạy. Hibernate chỉ kiểm tra schema (`ddl-auto: validate`), không tự tạo bảng.

**Phát sự kiện** vào exchange chung bằng class trong `common-lib`:

```java
rabbitTemplate.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_PAID,
        new OrderPaid(UUID.randomUUID(), Instant.now(), orderId, userId, eventId));
```

Listener phải idempotent theo `messageId` (BR-11). Message lỗi sau 3 lần thử chuyển vào queue `<queue>.dlq`.

**Feign:** client đã khai báo trong gói `client` của từng service. Lỗi 4xx như 409 hết vé không tính vào circuit breaker. Retry và fallback của KB-1 đến KB-3 do người phụ trách service thêm. Chỉ retry lời gọi idempotent.

## API Identity

Tất cả đi qua Gateway. Chi tiết request và response xem Swagger của Identity.

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Công khai | Đăng ký, trả token |
| POST | `/api/auth/login` | Công khai | Đăng nhập |
| POST | `/api/auth/google` | Công khai | Đăng nhập bằng `idToken` của Google Identity Services |
| POST | `/api/auth/refresh` | Công khai | Đổi refresh token lấy cặp token mới |
| POST | `/api/auth/logout` | Công khai | Thu hồi refresh token |
| GET, PUT | `/api/users/me` | Đăng nhập | Xem, sửa hồ sơ |
| PUT | `/api/users/me/password` | Đăng nhập | Đổi mật khẩu, thu hồi mọi phiên |
| GET | `/api/users?keyword&status&page&size` | ADMIN | Tìm người dùng |
| GET | `/api/users/{id}` | ADMIN | Xem người dùng |
| PUT | `/api/users/{id}/lock`, `/unlock` | ADMIN | Khóa, mở khóa |
| GET | `/internal/users/{id}` | Nội bộ | Email, họ tên, trạng thái |
| GET | `/internal/users/by-email?email` | Nội bộ | Tìm user theo email (không phân biệt hoa thường), 404 nếu không có; trả cả `status` và `admin`. Event dùng để mời thành viên tổ chức, thêm STAFF |
| POST | `/internal/users/batch` | Nội bộ | Tối đa 500 id |

Lưu ý cho SPA:

- Access token sống 15 phút, refresh token 7 ngày. Vai trò mới có hiệu lực từ lần refresh tiếp theo.
- Mỗi refresh token chỉ dùng được một lần, và dùng lại token cũ sẽ thu hồi **mọi phiên** của tài khoản. Khi nhiều request cùng gặp 401, SPA chỉ được gọi refresh một lần rồi cho các request đó chờ kết quả.

## API Event

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| GET | `/api/events?keyword&category&city&from&to&page&size` | Công khai | Tìm sự kiện PUBLISHED; `category` là slug, `from`/`to` dạng ISO-8601 |
| GET | `/api/events/{id}` | Công khai | Sự kiện chưa công bố chỉ người quản lý, STAFF và ADMIN xem được |
| GET | `/api/events/my?status` | Đăng nhập, trừ ADMIN | Sự kiện mình làm HOST, mọi trạng thái |
| GET | `/api/events/admin?keyword&status` | ADMIN | Mọi sự kiện, ví dụ `status=PENDING_APPROVAL` để duyệt |
| POST, PUT, DELETE | `/api/events`, `/api/events/{id}` | Người quản lý sự kiện | Tạo nháp, `organizationId` không bắt buộc; sửa, xóa khi DRAFT hoặc REJECTED |
| PUT | `/api/events/{id}/submit` | Người quản lý sự kiện | DRAFT/REJECTED → PENDING_APPROVAL |
| PUT | `/api/events/{id}/approve`, `/reject` | ADMIN | → PUBLISHED, hoặc → REJECTED kèm `{"reason"}` |
| PUT | `/api/events/{id}/cancel` | Người quản lý hoặc ADMIN | PUBLISHED → CANCELLED, phát `EventCancelled` |
| POST | `/api/events/{id}/images` | Người quản lý sự kiện | Multipart `file` (JPG/PNG/WEBP, tối đa 5 MB) lên Cloudinary, trả `{url, publicId}`; đưa `url` vào `bannerUrl` rồi lưu. Cần `CLOUDINARY_*` trong `.env` |
| PUT | `/api/events/{id}/host` | Người quản lý sự kiện | `{"userId"}`: đổi HOST, chỉ với sự kiện thuộc tổ chức; HOST mới phải là thành viên |
| GET, POST, DELETE | `/api/events/{id}/staff`, `/staff/{userId}` | Người quản lý sự kiện | Danh sách kèm họ tên, thêm bằng `{"email"}` (không cần thuộc tổ chức), xóa STAFF |
| POST | `/api/organizations` | Đăng nhập, trừ ADMIN | Tạo tổ chức, người tạo là OWNER |
| GET | `/api/organizations/my` | Đăng nhập, trừ ADMIN | Tổ chức mình tham gia, kèm `myRole`, `canCreateEvents`, `memberCount` |
| GET | `/api/organizations/{id}`, `/members`, `/events?status` | Thành viên | Người ngoài nhận 404; `/members` kèm họ tên, email từ Identity |
| PUT | `/api/organizations/{id}` | OWNER | Sửa tên, mô tả |
| POST | `/api/organizations/{id}/members` | OWNER, MANAGER | `{"email", "role"}`: mời MANAGER (chỉ OWNER) hoặc MEMBER |
| PUT | `/api/organizations/{id}/members/{userId}` | OWNER | `{"role", "canCreateEvents"}`: đổi vai trò, cấp quyền tạo sự kiện cho MEMBER |
| DELETE | `/api/organizations/{id}/members/{userId}` | OWNER, MANAGER (chỉ MEMBER), bản thân | Xóa hoặc tự rời; không xóa được OWNER và người đang làm HOST |
| PUT | `/api/organizations/{id}/owner` | OWNER | `{"userId"}`: chuyển quyền cho một thành viên, OWNER cũ thành MANAGER |
| GET | `/api/categories` | Công khai | Danh mục; slug sinh từ tên |
| POST, PUT, DELETE | `/api/categories`, `/api/categories/{id}` | ADMIN | Không xóa được danh mục đang dùng |
| GET | `/api/venues?city`, `/api/venues/{id}` | Công khai | Địa điểm |
| POST, PUT, DELETE | `/api/venues`, `/api/venues/{id}` | ADMIN | Thêm, sửa, xóa; không xóa được địa điểm đang dùng |
| GET | `/internal/events/{id}` | Nội bộ | `id, organizerId, title, status, startTime, endTime` cho Order và Ticket |
| GET | `/internal/events/{id}/access?userId` | Nội bộ | `canManage, canCheckIn`: Ticket và Order dùng để kiểm tra quyền thay vì so `organizerId` |

"Người quản lý" sự kiện là HOST (`organizerId`), hoặc OWNER/MANAGER của tổ chức sở hữu sự kiện; MEMBER chỉ quản lý sự kiện mình làm HOST. Bảng quyền đầy đủ ở mục 1.2 của [requirement.md](requirement.md).

Job mỗi phút chuyển sự kiện PUBLISHED đã qua `end_time` sang COMPLETED.

## API Payment

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| POST | `/api/payments` | Đăng nhập | `{"orderId"}`: hỏi Order (`/internal/orders/{id}`) số tiền và trạng thái, đơn phải của mình, PENDING, còn hạn. Trả `{payment, paymentUrl}` |
| GET | `/api/payments/{id}` | Người trả tiền, ADMIN | Người khác nhận 404. Giao dịch payOS còn chờ thì hỏi lại payOS (`GET /v2/payment-requests/{orderCode}`) |
| POST | `/api/payments/payos-webhook` | Công khai | payOS báo có tiền vào; kiểm tra chữ ký HMAC-SHA256 trên `data`, sai thì 400. orderCode không có trong hệ thống (dữ liệu thử) thì vẫn trả 200 |
| GET | `/api/payments/vnpay-return` | Công khai | VNPay chuyển trình duyệt về; kiểm tra chữ ký HMAC-SHA512 rồi chuyển tiếp tới `<FRONTEND_URL>/payments/result?paymentId=` (hoặc `?error=`) |
| POST | `/api/payments/{id}/mock-result` | Người trả tiền | `{"success"}`: chỉ với giao dịch cổng giả lập |
| GET | `/api/payments?status&page&size` | ADMIN | Mọi giao dịch, mới nhất trước |
| PUT | `/api/payments/{id}/refunded` | ADMIN | REFUND_PENDING → REFUNDED, sau khi đã hoàn tiền thủ công |

- **Cổng thanh toán**, chọn theo thứ tự:
  1. **payOS** khi có đủ `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`: người mua quét VietQR, tiền vào tài khoản ngân hàng đã liên kết ở my.payos.vn. payOS **không có sandbox**, thử bằng số tiền nhỏ.
  2. **VNPay sandbox** khi có `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`.
  3. **Cổng giả lập** khi không cấu hình gì: `paymentUrl` trỏ tới trang `/payments/{id}/mock` của SPA.
- **Webhook payOS:** khai báo `https://<domain công khai>/api/payments/payos-webhook` ở my.payos.vn. Chạy ở máy cá nhân thì payOS không gọi vào được; trang kết quả tự hỏi lại payOS nên vẫn cập nhật được trạng thái.
- **Message:** phát `PaymentSucceeded` hoặc `PaymentFailed` sau khi commit; nhận `OrderRefundRequested` thì chuyển giao dịch SUCCESS của đơn sang REFUND_PENDING (bỏ qua message trùng theo `messageId`).
- **Callback trùng (BR-11):** khóa dòng theo `transaction_ref`; mọi callback được ghi vào `payment_logs`, giao dịch đã xử lý thì không đổi nữa.
