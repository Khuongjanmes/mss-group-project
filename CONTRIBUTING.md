# Quy trình làm một tính năng mới

Làm theo đúng thứ tự dưới đây cho **mọi** tính năng, sửa lỗi hay việc cấu hình. Đề MSS301 chấm quy trình (issue, PR theo mẫu, nhánh, board, milestone) và chấm điểm cá nhân theo lịch sử commit, nên bỏ bước nào là mất điểm bước đó.

```
Issue ─► nhánh từ develop ─► code + test ─► commit (Refs #n) ─► kiểm thử ─► push
      ─► PR (Closes #n) ─► review ─► sửa bằng commit mới ─► approve ─► merge ─► xóa nhánh
```

**4 quy tắc không được phá:**
1. Không có issue thì không code.
2. Không push thẳng `main` hay `develop`.
3. Người viết không tự approve, không tự merge khi chưa có approve.
4. Không commit `.env`, mật khẩu, khóa API, `target/`, `node_modules/`.

**Phân công** (cũng có trong [`.github/CODEOWNERS`](.github/CODEOWNERS)):

| Thành viên | Tài khoản | Phụ trách |
| --- | --- | --- |
| TV1 (nhóm trưởng) | @YuiGuende | Identity, Gateway, Eureka, Notification, hạ tầng |
| TV2 | @duev-dev | Event, Payment |
| TV3 | @TranQuan1105 | Ticket |
| TV4 | @Khuongjanmes | Order |

Mỗi người làm service của mình. Cần sửa service của người khác thì nhắn người đó trước và mời họ review.

---

## Bước 0. Chuẩn bị máy (làm một lần)

```bash
git config --global user.name "Ho Ten"
git config --global user.email "email-cua-tai-khoan-github@..."   # sai email thì commit không được tính cho bạn
git config --global pull.rebase true
git config --global core.autocrlf true
cp .env.example .env                                              # điền giá trị; .env không bao giờ commit
```

Kiểm tra email: mở một commit của mình trên GitHub, avatar và tên tài khoản phải hiện đúng.

## Bước 1. Tạo issue

GitHub → **Issues → New issue** → chọn mẫu **Chức năng** (hoặc **Lỗi**).

| Ô | Điền |
| --- | --- |
| Title | `[FEATURE] <Service>: <việc cần làm>`, ví dụ `[FEATURE] Ticket: check-in bằng mã vé` |
| Mô tả | Làm gì, cho vai trò nào, thuộc chức năng nào trong [requirement.md](requirement.md) mục 1.2 |
| Tiêu chí chấp nhận | Checklist kiểm tra được: API trả mã gì, quy tắc BR-xx nào, test nào |
| Assignees | Chính mình |
| Labels | `type: feature` · `module: <service>` · `priority: <high/medium/low>` · thêm `bonus` nếu là mục nâng cao |
| Milestone | Checkpoint mà việc này thuộc về: `CP1`–`CP4` (xem requirement.md mục 4.1) |
| Projects | Board của nhóm, cột **To do** |

Ghi lại **số issue** (ví dụ `#21`). Số này dùng ở tên nhánh, commit và PR.

## Bước 2. Tạo nhánh từ `develop`

```bash
git switch develop
git pull
git switch -c feature/21-ticket-check-in        # feature/<số issue>-<service>-<tính năng>
```

| Loại việc | Tiền tố nhánh |
| --- | --- |
| Chức năng | `feature/` |
| Sửa lỗi | `fix/` |
| Cấu hình, hạ tầng, quy trình | `chore/` |
| Tài liệu, báo cáo | `docs/` |

Trên board, kéo issue sang **In progress**.

## Bước 3. Code và test

Mỗi tính năng back-end cần đủ:

- [ ] **Controller → Service → Repository**, DTO riêng cho request/response, không trả entity ra ngoài.
- [ ] **Bean Validation** trên DTO (`@NotBlank`, `@Size`…). Lỗi nghiệp vụ ném `ApiException` (mã lỗi viết hoa, ví dụ `SOLD_OUT`); `@ControllerAdvice` trong `common-lib` trả định dạng lỗi chung.
- [ ] **Phân quyền**: `@PreAuthorize` theo vai trò; quyền theo dữ liệu (ví dụ "chỉ người quản lý sự kiện") kiểm tra trong service.
- [ ] **Migration Flyway** mới (`V<n+1>__mo_ta.sql`) nếu đổi bảng. **Không sửa migration đã merge.**
- [ ] **Unit test tầng service** (JUnit 5 + Mockito) — bắt buộc theo đề cho mọi service.
- [ ] **Integration test** (`*IT.java`, Testcontainers) cho API chính nếu có.
- [ ] Gọi service khác qua **OpenFeign**, không giữ transaction trong lúc gọi mạng, có xử lý khi service kia lỗi.
- [ ] Message RabbitMQ: consumer bỏ qua message trùng theo `messageId` (BR-11).
- [ ] Cập nhật **README** (bảng API) và **requirement.md** nếu đổi API hay nghiệp vụ; thêm request vào **Postman collection**.

Front-end: chỉ gọi API qua Gateway (`src/api/*`), có trạng thái đang tải / lỗi / rỗng, toast sau thao tác thành công.

## Bước 4. Commit

Commit nhỏ, **mỗi commit build được**, theo Conventional Commits, dòng thứ hai là `Refs #<số issue>`:

```bash
git add ticket-service/src/main/java/...          # chỉ thêm file liên quan, không dùng "git add ." bừa
git commit -m "feat(ticket): check-in bằng mã vé" -m "Refs #21"
```

| Loại | Dùng khi |
| --- | --- |
| `feat` | Chức năng mới |
| `fix` | Sửa lỗi |
| `test` | Thêm hoặc sửa test |
| `refactor` | Sửa cấu trúc, không đổi hành vi |
| `docs` | README, requirement.md, báo cáo |
| `chore` | Cấu hình, build, CI, quy trình |

Phần trong ngoặc là service: `identity`, `gateway`, `event`, `ticket`, `order`, `payment`, `notification`, `frontend`.

Trước mỗi lần commit, xem lại: `git status` và `git diff --staged`. Thấy `.env`, khóa API hay file build thì bỏ ra.

## Bước 5. Tự kiểm thử trước khi push

```bash
./mvnw -pl <service> -am verify      # ví dụ ./mvnw -pl ticket-service -am verify (cần Docker cho *IT)
cd frontend && npm run build          # nếu có sửa frontend
```

Đổi API hay message dùng chung (`common-lib`, Gateway) thì chạy test của các service bị ảnh hưởng. Nên chạy thử bằng `docker compose up --build` rồi gọi qua Gateway (`http://localhost:8080`).

Ghi lại lệnh đã chạy và kết quả (ví dụ `TicketApiIT 8/8`) để dán vào PR.

## Bước 6. Cập nhật theo `develop` rồi push

```bash
git fetch origin
git rebase origin/develop             # chỉ rebase khi PR CHƯA có ai review
git push -u origin feature/21-ticket-check-in
```

Có xung đột: sửa file, `git add <file>`, `git rebase --continue`, chạy lại Bước 5.

## Bước 7. Mở Pull Request

GitHub → **Compare & pull request**. Base: **`develop`**.

| Ô | Điền |
| --- | --- |
| Title | Giống commit chính, ví dụ `feat(ticket): check-in bằng mã vé` |
| Mô tả | Theo mẫu tự hiện: dòng đầu **`Closes #21`**, mục *Thay đổi*, *Cách kiểm thử*, *Ảnh chụp* (nếu có giao diện), checklist |
| Reviewers | CODEOWNERS tự mời; nếu PR chỉ sửa service của mình, mời thêm một thành viên khác |
| Assignees, Labels, Milestone | Giống issue |

Kiểm tra cột phải **Development** hiện đúng issue. Trên board, kéo issue sang **In review**.

## Bước 8. Review

**Người review** (khác người viết):

- [ ] Đọc hết code, không chỉ lướt. Chạy thử nếu là luồng quan trọng.
- [ ] Đúng tiêu chí chấp nhận của issue và quy tắc BR-xx liên quan.
- [ ] Có test, test qua; không có bí mật trong code.
- [ ] Comment cụ thể từng dòng; chọn **Approve** hoặc **Request changes**.

**Người viết** khi bị yêu cầu sửa: sửa bằng **commit mới** (`fix(ticket): ...` + `Refs #21`), push lên cùng nhánh, trả lời từng comment rồi bấm *Resolve*. **Không force-push** sau khi đã có review.

## Bước 9. Merge

Chỉ merge khi: đã có **ít nhất 1 approve**, mọi comment đã resolve, test qua, không xung đột.

1. Bấm **Squash and merge** (tiêu đề giữ dạng Conventional Commits) hoặc theo cách nhóm trưởng chốt.
2. Bấm **Delete branch**.
3. Nếu issue chưa tự đóng (khi nhánh mặc định chưa phải `develop`): đóng tay, comment `Hoàn thành qua PR #<số>`.
4. Board: issue sang **Done**.
5. Trên máy:
   ```bash
   git switch develop
   git pull
   git branch -d feature/21-ticket-check-in
   ```

---

## Checklist rút gọn (copy vào ghi chú của mình)

```
[ ] 1. Issue: mẫu, tiêu chí chấp nhận, assignee, label type/module/priority, milestone CP, board
[ ] 2. Nhánh: feature/<số>-<service>-<tính năng> từ develop mới nhất; board → In progress
[ ] 3. Code: DTO + validation, ApiException, phân quyền, migration mới, unit test Mockito, IT
[ ] 4. Commit: feat(<service>): ... + "Refs #<số>"; không .env / khóa / target / node_modules
[ ] 5. Test: ./mvnw -pl <service> -am verify; npm run build; ghi kết quả
[ ] 6. Rebase develop (khi chưa review), push
[ ] 7. PR vào develop: "Closes #<số>", cách kiểm thử, ảnh; reviewer; label/milestone; board → In review
[ ] 8. Sửa theo review bằng commit mới, resolve comment
[ ] 9. Approve → merge → xóa nhánh → đóng issue nếu chưa tự đóng → board Done → pull develop
```

## Dùng AI

Đề cho phép dùng công cụ AI, nhưng:

- Ghi rõ phần nào dùng AI vào **mục 3 của báo cáo** (System Architecture).
- Người commit phải **hiểu và giải thích được mọi dòng** mình nộp; không giải thích được thì phần đó 0 điểm khi demo.
- Đọc lại code AI sinh ra trước khi commit; không dán khóa API hay `.env` vào công cụ AI.
