Closes #<!-- số issue -->

## Thay đổi
<!-- Gạch đầu dòng theo service: API, nghiệp vụ, migration, màn hình -->

## Cách kiểm thử
<!-- Lệnh đã chạy và kết quả, ví dụ: ./mvnw -pl event-service -am verify (EventApiIT 15/15); npm run build -->

## Ảnh chụp
<!-- Với thay đổi giao diện. Bỏ trống nếu không có -->

## Checklist
<!-- Quy trình đầy đủ: CONTRIBUTING.md -->
- [ ] Có issue và PR này ghi `Closes #<số issue>`
- [ ] Mỗi commit theo Conventional Commits và có `Refs #<số issue>`
- [ ] `./mvnw verify` (service đã sửa) hoặc `npm run build` (frontend) chạy qua
- [ ] Không commit `.env`, mật khẩu, khóa API, `target/`, `node_modules/`
- [ ] Cập nhật README / requirement.md nếu đổi API hoặc nghiệp vụ
- [ ] Đã mời reviewer khác người viết (CODEOWNERS tự mời người phụ trách service)
