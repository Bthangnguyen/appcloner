# Hướng Dẫn Sử Dụng Ghidra Dịch Ngược Native Code (.so) Android

Tài liệu này hướng dẫn từng bước thiết lập **Ghidra**, nạp thư viện `.so` của App Cloner, cấu hình kiểu dữ liệu **JNI (`jni.h`)** để Decompiler xuất ra mã C rõ ràng nhất.

---

## 1. Môi Trường & Công Cụ Cần Thiết

1. **Ghidra**: Tải từ trang chủ chính thức [ghidra-sre.org](https://ghidra-sre.org/) hoặc [GitHub Releases](https://github.com/NationalSecurityAgency/ghidra/releases).
2. **Java Development Kit (JDK 17+)**:
   * Workspace đã có sẵn JDK 17 tại đường dẫn: `tools/jdk17/`
   * Khi chạy Ghidra lần đầu, nếu được hỏi `JAVA_HOME`, hãy trỏ về thư mục `tools/jdk17`.

---

## 2. Các Bước Nạp & Cấu Hình File `.so` Trong Ghidra

### Bước 1: Tạo Project Mới
1. Khởi chạy Ghidra (`ghidraRun.bat`).
2. Chọn menu **File** -> **New Project** -> Chọn **Non-Shared Project**.
3. Đặt tên Project: `AppCloner_Native_Analysis` và chọn thư mục lưu trữ.

### Bước 2: Import Thư Viện `.so`
1. Kéo thả file `.so` (trong thư mục `native_libs/arm64-v8a/libappcloner.so`) vào cửa sổ Project.
2. Ghidra sẽ tự động nhận diện:
   * **Format:** `Executable and Linking Format (ELF)`
   * **Language:** `AARCH64:LE:64:v8A (ARM-v8A 64-bit Little Endian)`
3. Bấm **OK** để hoàn tất Import.

### Bước 3: Cấu hình Auto-Analysis (Rất Quan Trọng)
1. Kích đúp vào file vừa import để mở giao diện **CodeBrowser**.
2. Ghidra sẽ hỏi: *"libappcloner.so has not been analyzed. Would you like to analyze it now?"* -> Chọn **Yes**.
3. Trong bảng chọn Analyzers, tích chọn thêm các mục sau để tăng tối đa độ chính xác của Decompiler:
   * [x] **ARM Demangler** (Giải mã tên hàm C++ bị mã hóa name mangling).
   * [x] **Decompiler Parameter ID** (Tự động suy luận kiểu tham số hàm).
   * [x] **Call Convention ID** (Xác định chuẩn truyền tham số ARM64).
   * [x] **Non-Returning Functions - Discovered**.
4. Bấm **Analyze** và đợi thanh tiến trình góc dưới bên phải hoàn tất (thường mất 1–3 phút).

---

## 3. Kỹ Thuật Đỉnh Cao: Nạp Struct `JNIEnv` Để Decompiler Hiển Thị Đúng Tên Hàm

Mặc định khi mở hàm `JNI_OnLoad` trong Ghidra, Decompiler sẽ hiển thị các lệnh gọi hàm JNI dạng con trỏ thô rất khó đọc:
```c
// TRƯỚC KHI CẤU HÌNH (Rất khó hiểu):
undefined8 JNI_OnLoad(long *param_1) {
    long lVar1;
    lVar1 = (**(code **)(*param_1 + 0x30))(param_1, 0x10004); // Không biết 0x30 là hàm gì!
    return 0x10004;
}
```

### Cách khắc phục (Chuyển sang JNI Types):
1. **Nạp Android Data Types Archive:**
   * Trong cửa sổ **Data Type Manager** (góc dưới bên trái), bấm vào biểu tượng menu con (3 dấu gạch / tam giác) -> Chọn **Open File Archive...**
   * Hoặc nhấp chuột phải vào `libappcloner.so` trong Data Type Manager -> chọn **Apply Data Types...**
2. **Ép kiểu tham số trong Decompiler:**
   * Tại cửa sổ **Decompiler**, nhấp chuột phải vào tham số đầu tiên `param_1` của hàm `JNI_OnLoad`.
   * Chọn **Retype Variable** (hoặc phím tắt `Ctrl + L`).
   * Nhập kiểu dữ liệu: `JavaVM *` (đối với `JNI_OnLoad`) hoặc `JNIEnv *` (đối với các hàm JNI thông thường).
3. **Kết quả sau khi ép kiểu:**
```c
// SAU KHI ÉP KIỂU (Rõ ràng 100%):
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    // Tự động giải mã các lệnh RegisterNatives, FindClass, GetMethodID!
    return JNI_VERSION_1_6;
}
```

---

## 4. Cách Tìm Kiếm Logic Nhanh Trên Ghidra

| Thao tác | Phím tắt / Menu | Mục đích |
| :--- | :--- | :--- |
| **Tìm chuỗi ký tự (Strings)** | **Window** -> **Defined Strings** | Tìm chuỗi `ro.build`, `android_id`, `su`, `magisk`, tên hàm JNI |
| **Tìm bảng hàm Export** | **Window** -> **Symbol Table** -> Filter `Exports` | Tìm `JNI_OnLoad`, `Java_*`, các API export |
| **Tìm hàm gọi tới (Xrefs)** | Phím **`X`** khi chọn biến / hàm | Xem những vị trí nào trong code gọi tới API `dlsym`, `open`, `ioctl` |
| **Đổi tên hàm / biến** | Phím **`L`** (Label) hoặc **`Ctrl+L`** (Type) | Đặt lại tên có nghĩa cho các hàm sau khi phân tích |
| **Chuyển đổi Disassembly <-> C** | Phím **`F5`** (Decompile) / Click qua lại | Đồng bộ giữa mã máy Assembly ARM64 và mã C giả |
