# Phân Tích Chuyên Sâu: `libsystem.so`

## 1. Tổng Quan Thư Viện
* **File:** `native_libs/arm64-v8a/libsystem.so`
* **Kích thước:** 10,128 bytes (10 KB)
* **Kiến trúc:** AArch64 (ARM 64-bit ELF)
* **Package Java tương ứng:** `net.typeblog.socks.system`

---

## 2. Bảng Hàm Xuất (Exported Symbols)

Thư viện này chứa các hàm C++ mang tên đã bị mã hóa (mangled), cùng các hàm xử lý file descriptor tầng thấp:

```text
0x00000b3c: _Z39Java_net_typeblog_socks_system_jnicloseP7_JNIEnvP8_jobjecti
           -> Java_net_typeblog_socks_system_jniclose(JNIEnv*, jobject, jint fd)

0x00000b44: _Z37Java_net_typeblog_socks_system_sendfdP7_JNIEnvP8_jobjectiP8_jstring
           -> Java_net_typeblog_socks_system_sendfd(JNIEnv*, jobject, jint fd, jstring path)

0x00000cf0: JNI_OnLoad
0x00000e2c: ancil_send_fds_with_buffer
0x00000f48: ancil_send_fds
0x00001000: ancil_send_fd
```

---

## 3. Cơ Chế Hoạt Động & Vai Trò Trong App Cloner

### Giao tiếp UNIX Domain Socket & File Descriptor Passing (Ancillary Data / SCM_RIGHTS)
Trong kiến trúc sandbox của App Cloner:
1. **Tính năng Fake Proxy / Socks5 / VPN riêng cho từng App Clone:**
   * App Cloner cho phép mỗi bản sao (clone) chạy qua một đường truyền mạng riêng biệt (Proxy, VPN độc lập).
   * Để làm được điều này mà không cần root thiết bị, ứng dụng tạo một giao diện mạng ảo cục bộ (TUN device) và điều hướng các kết nối TCP/UDP thông qua `libtun2socks.so` và `libpdnsd.so`.
2. **Vai trò của `libsystem.so` (`ancil_send_fd`):**
   * Hàm `ancil_send_fd` sử dụng cơ chế `sendmsg` của Linux với cờ `SCM_RIGHTS` để chuyển giao quyền sở hữu Socket File Descriptor (FD) từ tiến trình app sang tiến trình mạng proxy mà không bị ngắt kết nối.
   * `Java_net_typeblog_socks_system_sendfd`: Cầu nối JNI cho phép tầng Java gửi Socket FD trực tiếp xuống UNIX socket cục bộ của VPN server.

---

## 4. Ý Nghĩa Khi Dịch Ngược

* `libsystem.so` là module mạng chuyên dụng (Network Intercept & Routing Helper).
* Khi phân tích các tính năng liên quan đến Proxy/Fake IP/Bypass kiểm tra mạng của App Cloner, `libsystem.so` kết hợp cùng `libtun2socks.so` chính là nơi điều khiển luồng dữ liệu mạng.
