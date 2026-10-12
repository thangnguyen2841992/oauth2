# IP trong lịch sử đăng nhập

Gateway xác định IP từ kết nối đến gateway và chuỗi proxy đã được cấu hình tin cậy. Metadata chuyển sang user-service được ký bằng HMAC với khóa nội bộ, có thời hạn và gắn với method/path. Metadata này chỉ dùng cho audit, không cấp quyền truy cập. Header từ trình duyệt bị loại bỏ và tạo lại tại gateway; user-service bỏ qua metadata không hợp lệ và giữ IP kết nối trực tiếp.

Trong profile `local`, `security.login-audit.capture-local-network=true` chụp các địa chỉ IPv4 nội bộ trên interface đang hoạt động khi kết nối đến gateway trực tiếp qua loopback. Browser và gateway phải chạy trên cùng máy. Cấu hình này không áp dụng khi peer là proxy được tin cậy, khi client ở máy khác, hoặc có profile `prod`/`production`.

Nếu local gateway chạy trong container, VM, qua tunnel hoặc sau proxy, đặt `security.login-audit.capture-local-network=false`: IP interface gateway trong trường hợp này không đại diện cho máy người dùng. Browser không cung cấp một API đáng tin cậy để đọc IP LAN của máy từ xa.

`login_audit.local_addresses` là cột nullable varchar(512), lưu snapshot tại thời điểm đăng nhập. Profile hiện tại dùng Hibernate `ddl-auto=update`, cột được thêm khi khởi động user-service. Môi trường dùng `validate` cần migration trước:

```sql
ALTER TABLE login_audit ADD COLUMN local_addresses VARCHAR(512) NULL;
```

Không chạy lại câu SQL khi cột đã tồn tại. Bản ghi cũ không được gán IP LAN hiện tại, vì IP có thể đã thay đổi. Sau khi reload Maven và khởi động lại gateway/user-service, đăng nhập mới qua gateway để tạo snapshot; tải lại lịch sử chỉ đọc các bản ghi đã lưu.
