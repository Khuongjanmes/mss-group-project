# OpenEvent

[![Backend CI](https://github.com/Khuongjanmes/mss-group-project/actions/workflows/backend-ci.yml/badge.svg?branch=develop)](https://github.com/Khuongjanmes/mss-group-project/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/Khuongjanmes/mss-group-project/actions/workflows/frontend-ci.yml/badge.svg?branch=develop)](https://github.com/Khuongjanmes/mss-group-project/actions/workflows/frontend-ci.yml)

Nền tảng tổ chức và bán vé sự kiện cho môn MSS301. Yêu cầu và thiết kế nằm trong [requirement.md](requirement.md).

## Cấu trúc và phân công

| Thư mục | Cổng | Database | Phụ trách (TV, tài khoản GitHub) | Trạng thái |
| --- | --- | --- | --- | --- |
| [eureka-server/](eureka-server/) | 8761 | — | TV1 @YuiGuende | Hoàn thành |
| [api-gateway/](api-gateway/) | 8080 | — | TV1 @YuiGuende | Hoàn thành |
| [identity-service/](identity-service/) | 8081 | PostgreSQL `identity_db` | TV1 @YuiGuende | Hoàn thành |
| [notification-service/](notification-service/) | 8086 | MongoDB `notification_db` | TV1 @YuiGuende | Hoàn thành: email vé (#60), báo hủy sự kiện (#61), hộp thông báo (#62) |
| [event-service/](event-service/) | 8082 | PostgreSQL `event_db` | TV2 @duev-dev | Hoàn thành |
| [payment-service/](payment-service/) | 8085 | PostgreSQL `payment_db` | TV2 @duev-dev | Hoàn thành (chờ Order có `/internal/orders/{id}`) |
| [ticket-service/](ticket-service/) | 8083 | PostgreSQL `ticket_db` | TV3 @TranQuan1105 | Hoàn thành (chờ Order giữ vé qua `/internal/reservations`) |
| [order-service/](order-service/) | 8084 | PostgreSQL `order_db` | TV4 @Khuongjanmes | Hoàn thành |
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

## Dữ liệu mẫu

Sau khi `docker compose up -d --build` và các service đã healthy:

```bash
node scripts/seed.mjs        # Node 18+; đọc BOOTSTRAP_ADMIN_* trong .env; chạy lại không tạo trùng
```

Script gọi API qua Gateway và tạo: 5 danh mục, 4 địa điểm ở Đà Nẵng, tổ chức *FPT Event Club*, 8 sự kiện (6 đã duyệt, 1 chờ duyệt, 1 bản nháp). Loại vé chưa có: đăng nhập ban tổ chức, vào **Quản lý sự kiện → chi tiết sự kiện → Loại vé** để tạo.

| Tài khoản | Mật khẩu | Vai trò |
| --- | --- | --- |
| `admin@openevent.local` | `BOOTSTRAP_ADMIN_PASSWORD` trong `.env` | Admin: duyệt sự kiện, danh mục, địa điểm, giao dịch |
| `organizer@openevent.local` | `Organizer@123` | OWNER của FPT Event Club, HOST các sự kiện mẫu |
| `manager@openevent.local` | `Organizer@123` | MANAGER của FPT Event Club |
| `student1@openevent.local`, `student2@openevent.local` | `Student@123` | Người mua vé |

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

**Postman:** import [postman/OpenEvent.postman_collection.json](postman/OpenEvent.postman_collection.json), điền `adminPassword` (giá trị `BOOTSTRAP_ADMIN_PASSWORD`) ở tab *Variables*, ô *Current value*, rồi **Run collection**, bỏ chọn thư mục *Thủ công*. Collection tự tạo tài khoản, sự kiện, loại vé, đơn và chạy hết luồng đặt vé, thanh toán, check-in, thông báo, hủy sự kiện, hoàn tiền (khoảng 110 request). Bước thanh toán dùng cổng giả lập: trước khi chạy, bật cổng giả lập bằng **Git Bash** (PowerShell, cmd không gán được biến rỗng) `PAYOS_CLIENT_ID= PAYOS_API_KEY= PAYOS_CHECKSUM_KEY= VNPAY_TMN_CODE= VNPAY_HASH_SECRET= docker compose up -d payment-service`, chạy xong thì `docker compose up -d payment-service` để về cấu hình thật. Trong Runner của Postman Desktop, **bỏ tick Keep variable values**, nếu không lần chạy sau dùng lại token của lần trước và hỏng các bước kiểm tra người dùng. Chạy bằng dòng lệnh:

```bash
npx newman run postman/OpenEvent.postman_collection.json --env-var adminPassword=<mật khẩu admin> \
  --folder "0. Xác thực" --folder "1. Người dùng" --folder "2. Danh mục và địa điểm" --folder "3. Tổ chức" \
  --folder "4. Sự kiện" --folder "5. Loại vé" --folder "6. Đặt vé và thanh toán" --folder "7. Check-in" \
  --folder "8. Hủy sự kiện" --folder "9. Dọn dẹp tài khoản"
```

API mới thì thêm request vào collection (sửa bằng Postman rồi *Export* v2.1 đè lên file).

## Quy trình làm việc trên GitHub

Mỗi việc đi theo vòng: **issue → nhánh → commit → PR → review → merge**. Không push thẳng `main` hay `develop`. Hướng dẫn từng bước, lệnh, checklist, release và cấu hình repo: [CONTRIBUTING.md](CONTRIBUTING.md). Người phụ trách từng service: bảng [Cấu trúc và phân công](#cấu-trúc-và-phân-công).

1. **Issue trước khi code.** Tạo bằng mẫu *Chức năng* hoặc *Lỗi*, có tiêu chí chấp nhận, người nhận (Assignees), label (`type:`, `module:`, `priority:`), milestone theo checkpoint (`CP1`–`CP4`) và gắn vào Project board.
2. **Nhánh** tạo từ `develop`, tên `feature/<số issue>-<service>-<tính năng>`, ví dụ `feature/8-ticket-check-in`. Việc cấu hình dùng `chore/`, sửa lỗi dùng `fix/`.
3. **Commit** nhỏ (1 commit = 1 thay đổi logic), mỗi commit build được, theo Conventional Commits: subject **tiếng Anh**, body giải thích vì sao, footer `Refs: #<số issue>`. Chi tiết: [CONTRIBUTING.md Bước 4](CONTRIBUTING.md#bước-4-commit).
   ```bash
   git commit -m "feat(ticket): check in tickets by code" -m "Refs: #8"
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
| GET | `/api/events?keyword&category&city&from&to&timeline&page&size` | Công khai | Tìm sự kiện PUBLISHED; `category` là slug, `from`/`to` dạng ISO-8601. `timeline` (`UPCOMING`, `ONGOING`, `ENDED`) cho các tab của trang sự kiện; `ENDED` gồm cả sự kiện COMPLETED, mới kết thúc trước |
| GET | `/api/events/{id}` | Công khai | Sự kiện chưa công bố chỉ người quản lý, STAFF và ADMIN xem được |
| GET | `/api/events/my?status` | Đăng nhập, trừ ADMIN | Sự kiện mình làm HOST, mọi trạng thái |
| GET | `/api/events/check-in` | Đăng nhập, trừ ADMIN | Sự kiện mình soát vé được (HOST, STAFF, OWNER/MANAGER của tổ chức), đã công bố hoặc vừa kết thúc trong 24 giờ, sớm nhất trước |
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
| GET | `/internal/events/{id}` | Nội bộ | `id, organizerId, title, status, startTime, endTime` cho Order và Ticket; thêm `venueName, venueAddress, venueCity` cho email vé của Notification |
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
| GET | `/api/payments/my?orderId=…&orderId=…` | Đăng nhập | Giao dịch của tôi cho các đơn này (tối đa 100), mới nhất trước; đơn của người khác không trả về. Trang Đơn của tôi dùng để hiện Đang hoàn tiền / Đã hoàn tiền |

- **Cổng thanh toán**, chọn theo thứ tự:
  1. **payOS** khi có đủ `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`: người mua quét VietQR, tiền vào tài khoản ngân hàng đã liên kết ở my.payos.vn. payOS **không có sandbox**, thử bằng số tiền nhỏ.
  2. **VNPay sandbox** khi có `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`.
  3. **Cổng giả lập** khi không cấu hình gì: `paymentUrl` trỏ tới trang `/payments/{id}/mock` của SPA.
- **Webhook payOS:** khai báo `https://<domain công khai>/api/payments/payos-webhook` ở my.payos.vn. Chạy ở máy cá nhân thì payOS không gọi vào được; trang kết quả tự hỏi lại payOS nên vẫn cập nhật được trạng thái.
- **Message:** phát `PaymentSucceeded` hoặc `PaymentFailed` sau khi commit; nhận `OrderRefundRequested` thì chuyển giao dịch SUCCESS của đơn sang REFUND_PENDING (bỏ qua message trùng theo `messageId`).
- **Callback trùng (BR-11):** khóa dòng theo `transaction_ref`; mọi callback được ghi vào `payment_logs`, giao dịch đã xử lý thì không đổi nữa.

## API Ticket

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| GET | `/api/ticket-types?eventId`, `/api/ticket-types/{id}` | Công khai | Loại vé kèm `available` (tổng trừ đã bán và đang giữ) |
| POST | `/api/ticket-types` | Người quản lý sự kiện | `{eventId, name, price, totalQuantity, saleStart, saleEnd}`; mở bán kết thúc trước khi sự kiện bắt đầu (BR-07) |
| PUT, DELETE | `/api/ticket-types/{id}` | Người quản lý sự kiện | Sự kiện lấy từ loại vé trong DB. Không giảm tổng dưới đã bán cộng đang giữ (BR-06); chỉ xóa loại chưa từng được giữ |
| GET | `/api/tickets/my` | Đăng nhập | Vé của tôi, mới nhất trước; `code` dùng làm QR |
| POST | `/api/tickets/check-in` | Người quản lý sự kiện, STAFF | `{"code", "eventId"}`: mỗi vé một lần, trong những ngày diễn ra (BR-09). `eventId` (không bắt buộc) là sự kiện đang soát, vé sự kiện khác trả 409 `TICKET_NOT_IN_EVENT`. Trả họ tên, email người mua; quét lại trả 409 `ALREADY_CHECKED_IN` kèm giờ và người đã soát |
| GET | `/api/tickets?eventId`, `/api/tickets/stats?eventId` | Người quản lý sự kiện | Người tham dự (họ tên, email từ Identity); đã bán, đang giữ, đã check-in theo loại vé |
| POST | `/internal/reservations` | Nội bộ | `{orderId, items:[{ticketTypeId, quantity}]}`: giữ vé 10 phút, tất cả hoặc không; hết vé hoặc chưa mở bán trả 409; gọi lại cùng `orderId` trả phần đã giữ. Trả `unitPrice` để Order tính tiền |

- **Quyền:** Gateway chỉ yêu cầu đăng nhập; Ticket hỏi Event `/internal/events/{id}/access` cho mọi thao tác trên loại vé, thống kê, người tham dự, check-in. Event lỗi thì từ chối (503), riêng check-in dùng bản cache và chỉ HOST check-in được (KB-3).
- **Chống bán vượt (BR-01):** giữ, nhả, bán đều là một câu `UPDATE` có điều kiện; các cột số lượng `updatable = false` nên JPA không ghi đè. Test `hundredConcurrentOrdersNeverOversell`: 100 đơn cùng lúc vào 10 vé, đúng 10 đơn giữ được.
- **Message:** nhận `OrderPaid` → phát hành vé, phát `TicketIssued`; `OrderCancelled` → nhả vé; `EventCancelled` → hủy vé chưa dùng, nhả vé đang giữ, phát `TicketsCancelled`. Message trùng bị bỏ qua theo `messageId`.
- **Job:** mỗi phút nhả vé giữ quá hạn 10 phút cộng 2 phút chờ (`openevent.ticket.release-grace`), để Order hết hạn đơn trước.

## API Order

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| POST | `/api/orders` | Đăng nhập | `{eventId, items:[{ticketTypeId, quantity}]}`: tạo đơn PENDING và giữ vé 10 phút qua Ticket (KB-1). Trả `201` kèm `id, status, totalAmount, expiresAt, items[{ticketTypeId, quantity, unitPrice}]` |
| GET | `/api/orders/my?page&size` | Đăng nhập | Đơn của tôi, mới nhất trước, mọi trạng thái (PENDING, PAID, CANCELLED, EXPIRED). `size` 1–100, mặc định 20. Trả `{content, page, size, totalElements, totalPages}` |
| PUT | `/api/orders/{id}/cancel` | Chủ đơn | Hủy đơn PENDING (BR-10): chuyển CANCELLED, trả lại quota, phát `OrderCancelled` để Ticket nhả vé. Đơn của người khác: 404 `ORDER_NOT_FOUND`; đơn không còn PENDING: 409 `ORDER_NOT_PENDING` |
| GET | `/internal/orders/{id}` | Nội bộ | `id, userId, totalAmount, status, expiresAt` cho Payment (KB-2), khớp `OrderClient.OrderSummary`; Payment tự kiểm tra đơn của người gọi, còn PENDING, chưa hết hạn. Không có đơn thì 404 `ORDER_NOT_FOUND` |

Mã lỗi của `POST /api/orders`:

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `DUPLICATE_TICKET_TYPE` | Một loại vé ghi hai lần |
| 400 | `TICKET_EVENT_MISMATCH` | Loại vé không thuộc `eventId` đã chọn |
| 403 | `ACCOUNT_LOCKED` | Tài khoản bị khóa (BR-12) |
| 404 | `EVENT_NOT_FOUND` | Không có sự kiện |
| 409 | `EVENT_NOT_ON_SALE` | Sự kiện không ở trạng thái PUBLISHED (BR-04) |
| 409 | `QUOTA_EXCEEDED` | Vượt 5 vé cho một sự kiện, tính cả các đơn trước (BR-03) |
| 409 | `SOLD_OUT`, `SALE_NOT_OPEN` | Ticket từ chối giữ vé; mã và thông điệp lấy nguyên từ Ticket |
| 503 | `SERVICE_BUSY` | Identity, Event hoặc Ticket không phản hồi sau khi thử lại |

- **Thứ tự (KB-1):** Identity (tài khoản còn hoạt động) → Event (PUBLISHED) → giới hạn 5 vé và tạo đơn PENDING → Ticket giữ vé với `orderId` của đơn → so `eventId` Ticket trả về với đơn → ghi đơn giá Ticket trả, tính `totalAmount`. Giá không nhận từ client.
- **Giới hạn 5 vé (BR-03):** một câu `INSERT … ON CONFLICT DO UPDATE … WHERE quantity + n <= 5` trên `user_event_quotas`, nên request đồng thời không vượt. Test `concurrentOrdersNeverExceedFiveTicketsPerUser`: 10 đơn cùng lúc, đúng 5 đơn được tạo. Đơn bị hủy thì trả lại quota.
- **Hạn đơn (BR-02):** 10 phút (`openevent.order.hold-for`), không muộn hơn `expiresAt` Ticket trả về. Job chạy mỗi phút (`openevent.order.expire-interval`) chuyển đơn PENDING quá hạn sang EXPIRED, trả lại quota, phát `OrderCancelled`. Ticket chỉ tự nhả vé sau hạn 2 phút, nên đơn luôn hết hạn trước.
- **Hủy và hết hạn cùng lúc:** đơn bị khóa dòng (`SELECT … FOR UPDATE`) khi đổi trạng thái, nên quota không bị trả hai lần. Job lấy đơn bằng `FOR UPDATE SKIP LOCKED`: nhiều instance chạy cùng lúc thì mỗi đơn chỉ được xử lý một lần. Test `parallelExpiryJobsHandleEachOrderOnce`: 4 job chạy song song trên 20 đơn quá hạn, đúng 20 đơn EXPIRED và 20 message.
- **Lời gọi service khác (KB-1 bước 7):** lỗi mạng, timeout, 5xx được thử lại tối đa 3 lần (`openevent.order.remote.*`, Resilience4J retry), sau đó circuit breaker; lỗi 4xx không thử lại và không tính vào circuit breaker. Không giữ transaction DB trong lúc gọi mạng.
- **Khi không giữ được vé:** đơn chuyển CANCELLED và trả lại quota. Ticket không phản hồi hoặc trả vé của sự kiện khác thì phát `OrderCancelled` (sau khi commit) để Ticket nhả phần có thể đã giữ; Ticket từ chối 4xx thì không cần vì Ticket giữ "tất cả hoặc không".
- **Kết quả thanh toán (KB-4):**

  | Message nhận | Trạng thái đơn | Order làm gì |
  | --- | --- | --- |
  | `PaymentSucceeded` | PENDING, trả trước `expiresAt` | PAID, phát `OrderPaid` → Ticket phát hành vé |
  | `PaymentSucceeded` | PENDING, trả sau `expiresAt` | EXPIRED, trả quota, phát `OrderCancelled` và `OrderRefundRequested` (BR-13) |
  | `PaymentSucceeded` | EXPIRED hoặc CANCELLED | Không phát vé, phát `OrderRefundRequested` → Payment chuyển REFUND_PENDING (BR-13) |
  | `PaymentSucceeded` | PAID (giao dịch thứ hai) | Chỉ ghi log cảnh báo: Payment hoàn mọi giao dịch SUCCESS của đơn nên không yêu cầu hoàn ở đây |
  | `PaymentFailed` | PENDING | CANCELLED, trả quota, phát `OrderCancelled` → Ticket nhả vé |
  | `PaymentFailed` | Khác PENDING | Bỏ qua |

  "Trả trước hạn" so `occurredAt` của Payment với `expiresAt`. Message trùng bị bỏ qua theo `messageId` (`processed_messages`, BR-11). Đơn được khóa dòng trước khi đổi trạng thái, nên tiền về đúng lúc người dùng hủy thì chỉ một bên thắng; test `paymentRacingCancelEndsInOneConsistentOutcome`. Mọi message phát sau khi commit.
- **Sự kiện bị hủy (KB-5):** nhận `EventCancelled` thì mọi đơn PENDING và PAID của sự kiện chuyển CANCELLED, trả lại quota; đơn PAID phát `OrderRefundRequested` (`EVENT_CANCELLED`) để Payment chuyển REFUND_PENDING (BR-10). Đơn EXPIRED, CANCELLED giữ nguyên. Không phát `OrderCancelled` vì Ticket tự hủy vé và nhả vé giữ khi nhận `EventCancelled`. Tiền về sau khi sự kiện bị hủy được hoàn như thanh toán trên đơn CANCELLED.

## Notification

| Method | Đường dẫn | Quyền | Mô tả |
| --- | --- | --- | --- |
| GET | `/api/notifications/my?page&size` | Đăng nhập | Thông báo của tôi, mới nhất trước. `size` 1–100, mặc định 20. Trả `{content, page, size, totalElements, totalPages, unreadCount}`; mỗi thông báo `id, type (TICKET_ISSUED/EVENT_CANCELLED), title, content, channel, read, sentAt` |
| GET | `/api/notifications/my/unread-count` | Đăng nhập | `{unreadCount}` cho chuông trên thanh điều hướng (SPA hỏi mỗi 60 giây và khi quay lại tab) |
| PATCH | `/api/notifications/{id}/read` | Người nhận | 204, gọi lại không lỗi. Thông báo của người khác: 404 `NOTIFICATION_NOT_FOUND` |

SPA: chuông cạnh tài khoản (không hiện với Admin) với số chưa đọc và 5 thông báo mới nhất; trang `/notifications`. Bấm thông báo thì đánh dấu đã đọc và mở Vé của tôi (vé đã phát) hoặc Đơn của tôi (sự kiện bị hủy).

Service nhận message từ RabbitMQ:

| Message nhận | Notification làm gì |
| --- | --- |
| `TicketIssued` (KB-4) | Hỏi Identity email người mua (`/internal/users/{id}`), hỏi Event tên, giờ, địa điểm (`/internal/events/{id}`); gửi email HTML, mỗi vé một ảnh QR nhúng (nội dung QR là mã vé, giống trang "Vé của tôi"); lưu thông báo `TICKET_ISSUED` vào collection `notifications` |
| `TicketsCancelled` (KB-5) | Bỏ trùng danh sách người mua, hỏi Identity email theo lô (`POST /internal/users/batch`, tối đa 500 id mỗi lần), hỏi Event tên, giờ, địa điểm; gửi mỗi người một email báo hủy (vé không còn dùng được, đơn đã thanh toán chờ hoàn tiền, nút "Xem đơn của tôi"); lưu thông báo `EVENT_CANCELLED`. Tài khoản không còn tồn tại thì bỏ qua |

- **Không gửi trùng (BR-11):** unique index `(messageId, userId)` trên `notifications`; người đã có thông báo của message thì bỏ qua. Mỗi người được gửi email rồi lưu ngay, nên Identity, Event hoặc máy chủ mail lỗi giữa chừng thì message được thử lại (chờ 2, 6, 18, 54 giây) mà người đã nhận không bị gửi lần hai; hết lượt thử thì vào `notification-service.events.dlq`.
- **Cấu hình mail:** `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` trong `.env` (xem `.env.example`, ví dụ Gmail với App password). Để trống `MAIL_HOST` thì không gửi email, thông báo vẫn được lưu với `channel = IN_APP`.
