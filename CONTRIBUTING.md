# Quy trình làm một tính năng mới

Làm theo đúng thứ tự dưới đây cho **mọi** tính năng, sửa lỗi hay việc cấu hình. Đề MSS301 chấm quy trình (issue, PR theo mẫu, nhánh, board, milestone) và chấm điểm cá nhân theo lịch sử commit, nên bỏ bước nào là mất điểm bước đó.

```
Issue ─► nhánh từ develop ─► code + test ─► commit (Refs: #n) ─► kiểm thử ─► push
      ─► PR (Closes #n) ─► review ─► sửa bằng commit mới ─► approve ─► merge ─► xóa nhánh
```

**4 quy tắc không được phá:**
1. Không có issue thì không code.
2. Không push thẳng `main` hay `develop`.
3. Người viết không tự approve, không tự merge khi chưa có approve.
4. Không commit `.env`, mật khẩu, khóa API, `target/`, `node_modules/`.

**Phân công:** xem bảng [Cấu trúc và phân công](README.md#cấu-trúc-và-phân-công) trong README (nguồn duy nhất; [`.github/CODEOWNERS`](.github/CODEOWNERS) làm theo bảng đó). Mỗi người làm service của mình. Cần sửa service của người khác thì nhắn người đó trước và mời họ review.

---

## Bước 0. Chuẩn bị máy (làm một lần)

```bash
git config --global user.name "Ho Ten"
git config --global user.email "email-cua-tai-khoan-github@..."   # sai email thì commit không được tính cho bạn
git config --global pull.rebase true
git config --global core.autocrlf true
cp .env.example .env                                              # điền giá trị; .env không bao giờ commit
git config commit.template .gitmessage                            # chạy trong thư mục repo: "git commit" mở sẵn mẫu Bước 4
```

Kiểm tra email: mở một commit của mình trên GitHub, avatar và tên tài khoản phải hiện đúng.

## Bước 1. Tạo issue

GitHub → **Issues → New issue** → chọn mẫu **Chức năng** (hoặc **Lỗi**).

| Ô | Điền |
| --- | --- |
| Title | **Tiếng Anh**: `[FEATURE] <Service>: <việc cần làm>` (hoặc `[BUG]`, `[CHORE]`), ví dụ `[FEATURE] Ticket: check in tickets by code`. Nội dung issue viết tiếng Việt |
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

Viết theo quy ước của đề MSS301 (Conventional Commits). Áp dụng cho commit mới; commit đã merge giữ nguyên.

```
<type>(<scope>): <subject>
                                   ← dòng trống
<body: giải thích VÌ SAO, không chỉ liệt kê đã làm gì>
                                   ← dòng trống
Refs: #<số issue>
```

**Quy tắc bắt buộc:**

1. Subject viết **tiếng Anh**, động từ nguyên mẫu (`add`, `implement`, không phải `added`), chữ thường, **≤ 72 ký tự**, không dấu chấm cuối.
2. **1 commit = 1 thay đổi logic**, và mỗi commit build được. Không gộp "entity + API + message + frontend" vào một commit.
3. Footer `Refs: #<số issue>` (có dấu `:`) để đối chiếu commit với issue.
4. Thay đổi phá vỡ tương thích (đổi URL API, đổi schema, đổi message dùng chung) thì thêm `!` sau type/scope và footer `BREAKING CHANGE: <mô tả>`.
5. Viết **tiếng Anh**: subject commit, tiêu đề PR (thành commit khi squash, xem Bước 9) và tiêu đề issue (Bước 1). Viết **tiếng Việt**: nội dung issue, mô tả PR và comment review. Body commit được viết tiếng Việt.

```bash
git add ticket-service/src/main/java/...          # chỉ thêm file liên quan, không dùng "git add ." bừa
git commit                                        # mở trình soạn thảo theo mẫu .gitmessage (Bước 0)
# hoặc viết nhanh trên một dòng lệnh:
git commit -m "feat(ticket): check in tickets by code" -m "Staff scan QR at the gate, so look up the ticket by code." -m "Refs: #21"
```

| ❌ Sai | ✅ Đúng |
| --- | --- |
| `update code` | `feat(order): create pending order and reserve tickets` |
| `Fixed bug.` | `fix(gateway): strip client supplied user headers` |
| `feat(ticket): check-in bằng mã vé` | `feat(ticket): check in tickets by code` |
| `feat: thêm loại vé, giữ vé, check-in và frontend` | Tách thành nhiều commit, mỗi commit một thay đổi |

| Loại | Dùng khi |
| --- | --- |
| `feat` | Chức năng mới |
| `fix` | Sửa lỗi |
| `test` | Thêm hoặc sửa test |
| `refactor` | Sửa cấu trúc, không đổi hành vi |
| `docs` | README, requirement.md, báo cáo |
| `chore` | Cấu hình, build, CI, quy trình |

Phần trong ngoặc (scope) là **tùy chọn**, ghi phạm vi bị sửa:
- Service: `identity`, `gateway`, `event`, `ticket`, `order`, `payment`, `notification`, `frontend`.
- Phạm vi chung: `docker` (Dockerfile, compose), `postman` (collection), `github` (mẫu issue/PR, CODEOWNERS).
- Không thuộc phạm vi nào (ví dụ tài liệu quy trình chung như CONTRIBUTING.md) thì bỏ scope: `docs: adopt mss301 commit conventions`.

Một commit chỉ có một scope; sửa nhiều service thì thường là nên tách commit (quy tắc 2).

Trước mỗi lần commit, xem lại: `git status` và `git diff --staged`. Thấy `.env`, khóa API hay file build thì bỏ ra.

## Bước 5. Tự kiểm thử trước khi push

```bash
./mvnw -pl <service> -am verify      # ví dụ ./mvnw -pl ticket-service -am verify (cần Docker cho *IT)
cd frontend && npm run build          # nếu có sửa frontend
```

Đổi API hay message dùng chung (`common-lib`, Gateway) thì chạy test của các service bị ảnh hưởng. Nên chạy thử bằng `docker compose up --build` rồi gọi qua Gateway (`http://localhost:8080`).

Ghi lại lệnh đã chạy và kết quả (ví dụ `TicketApiIT 8/8`) để dán vào PR.

## Bước 6. Cập nhật theo `develop` rồi push

**PR chưa có ai review:** rebase cho lịch sử gọn.

```bash
git fetch origin
git rebase origin/develop
git push -u origin feature/21-ticket-check-in        # lần push đầu
git push --force-with-lease                          # nhánh đã push trước đó: rebase đổi commit nên push thường bị từ chối
```

**PR đã có review:** không rebase, không force-push (reviewer sẽ mất dấu phần đã xem). Gộp `develop` vào bằng merge:

```bash
git fetch origin
git merge origin/develop
git push
```

Có xung đột: sửa file, `git add <file>`, rồi `git rebase --continue` (đang rebase) hoặc `git commit` (đang merge); chạy lại Bước 5. Chỉ dùng `--force-with-lease`, không dùng `--force`, và chỉ trên nhánh của chính mình.

## Bước 7. Mở Pull Request

GitHub → **Compare & pull request**. Base: **`develop`**.

| Ô | Điền |
| --- | --- |
| Title | Theo quy ước commit ở Bước 4 (tiếng Anh), ví dụ `feat(ticket): check in tickets by code`. Khi squash, tiêu đề này thành commit trên `develop` |
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

**Người viết** khi bị yêu cầu sửa: sửa bằng **commit mới** (`fix(ticket): ...` + `Refs: #21`), push lên cùng nhánh, trả lời từng comment rồi bấm *Resolve*. **Không force-push** sau khi đã có review.

## Bước 9. Merge

Chỉ merge khi: đã có **ít nhất 1 approve**, mọi comment đã resolve, test qua, không xung đột.

1. Bấm **Squash and merge**: cả PR thành một commit trên `develop`. Trước khi bấm **Confirm squash and merge**, kiểm tra:
   - Tiêu đề: `<type>(<scope>): <subject> (#<số PR>)`, tiếng Anh theo Bước 4, ví dụ `feat(ticket): check in tickets by code (#21)`.
   - Mô tả: xóa danh sách commit con GitHub tự điền; giữ một đoạn ngắn vì sao, dòng `Closes #<số issue>` và các dòng `Co-authored-by:` nếu có.

   Merge commit chỉ dùng cho PR release vào `main` và PR back-merge (xem [Release vào main](#release-vào-main)).
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
[ ] 4. Commit: feat(<service>): <english subject> + body vì sao + "Refs: #<số>"; 1 commit = 1 thay đổi; không .env / khóa / target / node_modules
[ ] 5. Test: ./mvnw -pl <service> -am verify; npm run build; ghi kết quả
[ ] 6. Chưa review: rebase develop + push --force-with-lease · Đã review: merge develop + push
[ ] 7. PR vào develop: "Closes #<số>", cách kiểm thử, ảnh; reviewer; label/milestone; board → In review
[ ] 8. Sửa theo review bằng commit mới, resolve comment
[ ] 9. Approve → Squash and merge → xóa nhánh → đóng issue nếu chưa tự đóng → board Done → pull develop
```

## Cuối mỗi checkpoint: kiểm thử chéo

Trước mỗi CP, một người (xoay vòng, không phải người viết phần đó) chạy hệ thống từ `develop` bằng `docker compose up --build` và đi hết các luồng chính của checkpoint qua Gateway và giao diện. Mỗi lỗi tìm được tạo **một issue bằng mẫu *Lỗi***: gán người phụ trách service, label `type: bug`, milestone của CP đó. Sửa lỗi đi đúng quy trình như một tính năng (nhánh `fix/<số issue>-...`).

| Checkpoint | Người kiểm thử |
| --- | --- |
| CP2 | TV3 |
| CP3 | TV4 |
| CP4 | TV2 |

## Release vào main

Làm ở checkpoint nộp bài (ít nhất CP4). Nhóm trưởng thực hiện.

1. Tạo nhánh release từ `develop`:
   ```bash
   git switch develop && git pull
   git switch -c release/1.0.0
   git push -u origin release/1.0.0
   ```
2. Mở PR `release/1.0.0` → **`main`**, cần **2 approval**. Trên nhánh này chỉ sửa lỗi nhỏ (commit `fix(...)` + `Refs: #<issue lỗi>`).
3. Merge bằng **Create a merge commit** (không squash, để giữ lịch sử từng PR).
4. Gắn tag và tạo GitHub Release:
   ```bash
   git switch main && git pull
   git tag -a v1.0.0 -m "OpenEvent v1.0.0 (CP4)"
   git push origin v1.0.0
   ```
   GitHub → **Releases → Draft a new release** → chọn tag `v1.0.0` → *Generate release notes* → **Publish release**.
5. Back-merge: mở PR `main` → `develop`, merge bằng **Create a merge commit**, để `develop` có các sửa lỗi làm trên nhánh release.

## Checklist trước khi nộp (CP4)

- [ ] *Insights → Contributors* hiện đủ 4 thành viên, commit cân đối; ai thiếu thì kiểm tra lại email git (Bước 0).
- [ ] `git log --first-parent --oneline develop`: mỗi dòng là một PR đã merge, không có commit push thẳng.
- [ ] Mọi PR đã đóng đều có ít nhất 1 *Approved* của người khác người viết.
- [ ] Không còn issue mở trong milestone CP4 (hoặc đã chuyển milestone, có ghi lý do).
- [ ] `docker compose up` chạy toàn bộ theo README; có Postman collection, dữ liệu mẫu, tài khoản test.
- [ ] Repo không có `.env`, khóa API, `target/`, `node_modules/`: lệnh `git ls-files | grep -E "(^|/)\.env$|target/|node_modules/"` không ra dòng nào.
- [ ] Đã có tag và GitHub Release `v1.0.0`.

## Cấu hình repo (nhóm trưởng, quyền admin, làm một lần)

**Settings → General**
- *Default branch*: `develop`, để `Closes #n` tự đóng issue khi PR merge vào `develop`.
- *Pull Requests*: bật *Allow squash merging* với *Default commit message* = **Pull request title and description**; bật *Allow merge commits* (cho release và back-merge); **tắt** *Allow rebase merging*; bật *Automatically delete head branches*.

**Settings → Rules → Rulesets** (repo Private dùng GitHub Free không có mục này thì coi bảng dưới là quy ước bắt buộc):

| Ruleset | Nhánh | Cài đặt |
| --- | --- | --- |
| `protect-develop` | `develop` | *Restrict deletions* · *Block force pushes* · *Require a pull request before merging*: 1 approval, *Dismiss stale pull request approvals when new commits are pushed*, *Require conversation resolution before merging* |
| `protect-main` | `main` | Như trên nhưng **2 approval** và *Require review from Code Owners* |

Cả hai ruleset: **Bypass list → Repository admin**. Lý do: khi bật *Require review from Code Owners*, PR mà người mở là code owner duy nhất của thư mục (ví dụ nhóm trưởng mở PR release có sửa `identity-service`) sẽ không ai khác approve được.

**Project board** (*Projects → New project → Board*, rồi tab **Projects** của repo → **Link a project**):
- Cột: **Backlog → To do → In progress → In review → Done**.
- **⋯ → Workflows**, bật: *Item added to project* → Backlog · *Item closed* → Done · *Pull request merged* → Done.
- *Manage access*: thêm 3 thành viên quyền **Write**.


## Dùng AI

Đề cho phép dùng công cụ AI, nhưng:

- Ghi rõ phần nào dùng AI vào **mục 3 của báo cáo** (System Architecture).
- Người commit phải **hiểu và giải thích được mọi dòng** mình nộp; không giải thích được thì phần đó 0 điểm khi demo.
- Đọc lại code AI sinh ra trước khi commit; không dán khóa API hay `.env` vào công cụ AI.
- **Không** để dấu vết công cụ AI trong commit, PR, issue hay comment review: không có dòng `Co-Authored-By: Claude …` (hay của công cụ AI khác), không có dòng `🤖 Generated with …`, không có câu "soạn cùng AI". Công cụ tự thêm thì xóa trước khi commit hoặc trước khi tạo PR. Việc dùng AI chỉ khai báo trong báo cáo (mục 3). Commit được tính cho người commit, nên người đó chịu trách nhiệm toàn bộ nội dung.
