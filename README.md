# App Cloner Studio - Android App Virtualization & Customization

Dự án Android mã nguồn mở (Clean-room Implementation) cung cấp bộ công cụ nhân bản ứng dụng (App Cloning), can thiệp nhị phân AXML, giả lập thông số phần cứng (Device & Identity Spoofing) và ngụy trang ứng dụng.

---

## 🌟 Cấu trúc Dự án (Multi-Module)

* **`:app`**: Giao diện người dùng Material Design, quản lý danh sách ứng dụng đã cài đặt, tùy biến biểu tượng (Icon Hue/Flip/Badge), và chế độ ngụy trang máy tính (`CalculatorActivity`).
* **`:core-repackager`**: Engine xử lý file APK:
  * `AxmlEditor`: Phân tích và sửa đổi trực tiếp Binary `AndroidManifest.xml` nhị phân.
  * `ApkSignerHelper`: Ký số chứng chỉ RSA Scheme v1/v2/v3 tự động.
  * `ClonePipeline`: Điều phối toàn bộ quy trình clone từ APK gốc đến APK thành phẩm.
* **`:clone-runtime`**: Thư viện siêu nhẹ nhúng vào APK clone:
  * `AppClonerApplication`: Application Wrapper khởi động nạp cấu hình.
  * `PineHookManager`: Can thiệp động các API hệ thống (Android ID, IMEI Luhn, MAC OUI, GPS).
  * `RootHideHook`: Che giấu trạng thái Root / Magisk.
  * `IdentityGenerator`: Sinh thông số định danh thiết bị ngẫu nhiên.

---

## 🚀 Hướng dẫn Biên dịch (Build APK)

### Cách 1: Sử dụng Script tự động `build_apk.py`
```bash
python build_apk.py
```
File APK thành phẩm sẽ được xuất ra tại thư mục gốc: `AppCloner-Studio-v1.0.apk`.

### Cách 2: Mở trong Android Studio
1. Mở Android Studio $\rightarrow$ **Open** $\rightarrow$ Chọn thư mục `AppClonerProject`.
2. Đồng bộ Gradle và bấm **Run** (`Shift + F10`).
