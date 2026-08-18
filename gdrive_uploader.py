"""
SubAI PC to Google Drive Uploader
==================================
Hỗ trợ cả 2 phương thức xác thực:
1. OAuth 2.0 Client ID (client_secret.json) - Tự động đăng nhập Google 1 lần và cấp quyền vĩnh viễn.
2. Service Account JSON (service_account.json).

Tự động đồng bộ và đẩy video từ SubAI PC lên thư mục 'SubAI_Queue' trên Google Drive.
Sau khi upload xong, bạn có thể TẮT MÁY TÍNH THOẢI MÁI.
Điện thoại sẽ tự động lấy video từ Google Drive khi đến giờ hẹn để đăng lên TikTok.
"""

import os
import sys
import time
import json
import argparse
import webbrowser
import urllib.request
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timezone

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', line_buffering=True)
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', line_buffering=True)

TOKEN_FILE = "gdrive_token.json"
SCOPES = "https://www.googleapis.com/auth/drive"


class OAuthCallbackHandler(BaseHTTPRequestHandler):
    auth_code = None

    def do_GET(self):
        query = urllib.parse.urlparse(self.path).query
        params = urllib.parse.parse_qs(query)
        if "code" in params:
            OAuthCallbackHandler.auth_code = params["code"][0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            html = """
            <html><body style="font-family: sans-serif; text-align: center; padding-top: 50px;">
            <h1 style="color: #2E7D32;">✅ Đăng Nhập Google Drive Thành Công!</h1>
            <p>SubAI Uploader đã nhận được quyền truy cập. Bạn có thể đóng tab này và quay lại terminal.</p>
            </body></html>
            """
            self.wfile.write(html.encode('utf-8'))
        else:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Error: No code received.")

    def log_message(self, format, *args):
        pass


def perform_oauth_flow(client_id, client_secret, redirect_port=8080):
    redirect_uri = f"http://localhost:{redirect_port}"
    auth_url = (
        f"https://accounts.google.com/o/oauth2/v2/auth?"
        f"client_id={urllib.parse.quote(client_id)}&"
        f"redirect_uri={urllib.parse.quote(redirect_uri)}&"
        f"response_type=code&"
        f"scope={urllib.parse.quote(SCOPES)}&"
        f"access_type=offline&"
        f"prompt=consent"
    )

    print("\n=======================================================")
    print("🔑 ĐANG MỞ TRÌNH DUYỆT ĐỂ ĐĂNG NHẬP GOOGLE...")
    print("Nếu trình duyệt không tự mở, hãy truy cập link sau:")
    print(f"👉 {auth_url}")
    print("=======================================================\n")

    try:
        webbrowser.open(auth_url)
    except:
        pass

    server = HTTPServer(("localhost", redirect_port), OAuthCallbackHandler)
    while OAuthCallbackHandler.auth_code is None:
        server.handle_request()

    code = OAuthCallbackHandler.auth_code
    token_url = "https://oauth2.googleapis.com/token"
    token_data = urllib.parse.urlencode({
        "code": code,
        "client_id": client_id,
        "client_secret": client_secret,
        "redirect_uri": redirect_uri,
        "grant_type": "authorization_code"
    }).encode('utf-8')

    req = urllib.request.Request(token_url, data=token_data, headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req) as resp:
        token_res = json.loads(resp.read().decode('utf-8'))
        token_res["client_id"] = client_id
        token_res["client_secret"] = client_secret
        token_res["saved_at"] = int(time.time())

        with open(TOKEN_FILE, "w", encoding="utf-8") as f:
            json.dump(token_res, f, indent=2)

        print("[OK] Đã lưu thông tin xác thực OAuth2 vào 'gdrive_token.json' thành công!")
        return token_res.get("access_token")


def refresh_oauth_token(token_data):
    try:
        token_url = "https://oauth2.googleapis.com/token"
        req_data = urllib.parse.urlencode({
            "client_id": token_data["client_id"],
            "client_secret": token_data["client_secret"],
            "refresh_token": token_data["refresh_token"],
            "grant_type": "refresh_token"
        }).encode('utf-8')

        req = urllib.request.Request(token_url, data=req_data, headers={"Content-Type": "application/x-www-form-urlencoded"})
        with urllib.request.urlopen(req) as resp:
            new_tokens = json.loads(resp.read().decode('utf-8'))
            token_data["access_token"] = new_tokens["access_token"]
            token_data["expires_in"] = new_tokens.get("expires_in", 3600)
            token_data["saved_at"] = int(time.time())

            with open(TOKEN_FILE, "w", encoding="utf-8") as f:
                json.dump(token_data, f, indent=2)

            return token_data["access_token"]
    except Exception as e:
        print(f"[!] Lỗi gia hạn token: {e}")
        return None


class GoogleDriveUploader:
    def __init__(self, client_secret_path="client_secret.json", sa_path="service_account.json"):
        self.cs_path = client_secret_path
        self.sa_path = sa_path
        self.access_token = None
        self.queue_folder_id = None
        self.done_folder_id = None
        self.init_auth()

    def init_auth(self):
        # 1. Kiểm tra nếu đã có gdrive_token.json
        if os.path.exists(TOKEN_FILE):
            try:
                with open(TOKEN_FILE, "r", encoding="utf-8") as f:
                    tdata = json.load(f)
                now = int(time.time())
                saved_at = tdata.get("saved_at", 0)
                expires_in = tdata.get("expires_in", 3600)
                if now - saved_at < expires_in - 300:
                    self.access_token = tdata.get("access_token")
                else:
                    self.access_token = refresh_oauth_token(tdata)

                if self.access_token:
                    print("[OK] Đã nạp Google Drive Access Token từ token lưu trữ.")
                    self.ensure_folders()
                    return True
            except Exception as e:
                print(f"[!] Lỗi đọc token: {e}")

        # 2. Kiểm tra OAuth2 client_secret.json
        if os.path.exists(self.cs_path):
            with open(self.cs_path, "r", encoding="utf-8") as f:
                cs_data = json.load(f)
            installed = cs_data.get("installed") or cs_data.get("web") or {}
            cid = installed.get("client_id")
            csec = installed.get("client_secret")
            if cid and csec:
                self.access_token = perform_oauth_flow(cid, csec)
                if self.access_token:
                    self.ensure_folders()
                    return True

        print("[ERROR] Chưa thể xác thực Google Drive. Vui lòng kiểm tra 'client_secret.json'.")
        return False

    def api_request(self, url, method="GET", headers=None, data=None):
        if headers is None:
            headers = {}
        headers["Authorization"] = f"Bearer {self.access_token}"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            err_body = e.read().decode('utf-8')
            print(f"[API Error] {e.code}: {err_body}")
            return None

    def find_or_create_folder(self, folder_name):
        query = urllib.parse.quote(f"name = '{folder_name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
        url = f"https://www.googleapis.com/drive/v3/files?q={query}&fields=files(id,name)"
        res = self.api_request(url)
        if res and res.get("files"):
            return res["files"][0]["id"]

        create_url = "https://www.googleapis.com/drive/v3/files"
        meta = {
            "name": folder_name,
            "mimeType": "application/vnd.google-apps.folder"
        }
        headers = {"Content-Type": "application/json"}
        res = self.api_request(create_url, method="POST", headers=headers, data=json.dumps(meta).encode('utf-8'))
        if res:
            return res.get("id")
        return None

    def ensure_folders(self):
        print("--> Đang kiểm tra thư mục 'SubAI_Queue' & 'SubAI_Done' trên Google Drive...")
        self.queue_folder_id = self.find_or_create_folder("SubAI_Queue")
        self.done_folder_id = self.find_or_create_folder("SubAI_Done")
        print(f"    [SubAI_Queue Folder ID]: {self.queue_folder_id}")
        print(f"    [SubAI_Done Folder ID]: {self.done_folder_id}")

    def upload_file(self, file_path, folder_id, description=""):
        if not os.path.exists(file_path):
            print(f"[!] File không tồn tại: {file_path}")
            return None

        file_name = os.path.basename(file_path)
        file_size = os.path.getsize(file_path)
        mime_type = "video/mp4" if file_name.endswith(".mp4") else "application/json"

        print(f"--> Đang tải lên Google Drive: {file_name} ({file_size:,} bytes)...")

        init_url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
        metadata = {
            "name": file_name,
            "parents": [folder_id],
            "description": description
        }
        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json; charset=UTF-8",
            "X-Upload-Content-Type": mime_type,
            "X-Upload-Content-Length": str(file_size)
        }
        req = urllib.request.Request(init_url, data=json.dumps(metadata).encode('utf-8'), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                upload_url = resp.headers.get("Location")
        except Exception as e:
            print(f"[!] Lỗi khởi tạo upload: {e}")
            return None

        if not upload_url:
            return None

        with open(file_path, "rb") as f:
            file_data = f.read()

        upload_headers = {
            "Content-Length": str(file_size),
            "Content-Type": mime_type
        }
        upload_req = urllib.request.Request(upload_url, data=file_data, headers=upload_headers, method="PUT")
        try:
            with urllib.request.urlopen(upload_req) as resp:
                res = json.loads(resp.read().decode('utf-8'))
                file_id = res.get("id")
                print(f"    [OK] Đã tải lên thành công! File ID: {file_id}")
                return file_id
        except Exception as e:
            print(f"[!] Lỗi truyền tải file: {e}")
            return None

    def upload_subai_video(self, video_path, caption="", hashtags="", target_clone="clone1", schedule_time=""):
        if not self.queue_folder_id:
            self.ensure_folders()

        video_id = self.upload_file(video_path, self.queue_folder_id, description=caption)
        if not video_id:
            return False

        base_name = os.path.splitext(os.path.basename(video_path))[0]
        meta = {
            "video_file_id": video_id,
            "video_file_name": os.path.basename(video_path),
            "title": base_name,
            "caption": caption,
            "hashtags": hashtags,
            "target_clone": target_clone,
            "schedule_time": schedule_time,
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        meta_file = f"{video_path}.json"
        with open(meta_file, "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)

        meta_id = self.upload_file(meta_file, self.queue_folder_id)
        try:
            os.remove(meta_file)
        except:
            pass

        print(f"\n[HOÀN TẤT] Video '{os.path.basename(video_path)}' đã sẵn sàng trên Google Drive!")
        print("--> Bạn có thể TẮT PC. Điện thoại sẽ tự động tải và đăng theo lịch.\n")
        return True

    def watch_folder(self, folder_to_watch, target_clone="clone1"):
        print(f"\n[*] Bắt đầu theo dõi thư mục SubAI: {folder_to_watch}")
        print("    (Mỗi khi có video .mp4 mới xuất ra, script sẽ tự động đẩy lên Google Drive)\n")
        os.makedirs(folder_to_watch, exist_ok=True)
        processed_files = set()

        while True:
            try:
                for f in os.listdir(folder_to_watch):
                    if f.endswith(".mp4") and not f.startswith("temp_") and f not in processed_files:
                        fp = os.path.join(folder_to_watch, f)
                        s1 = os.path.getsize(fp)
                        time.sleep(2)
                        s2 = os.path.getsize(fp)
                        if s1 == s2 and s1 > 0:
                            txt_path = os.path.splitext(fp)[0] + ".txt"
                            caption = ""
                            hashtags = "#trending #xuhuong #subai"
                            if os.path.exists(txt_path):
                                with open(txt_path, "r", encoding="utf-8") as tf:
                                    caption = tf.read().strip()
                            else:
                                caption = os.path.splitext(f)[0]

                            print(f"\n[PHÁT HIỆN VIDEO MỚI] {f}")
                            if self.upload_subai_video(fp, caption=caption, hashtags=hashtags, target_clone=target_clone):
                                processed_files.add(f)
                time.sleep(5)
            except KeyboardInterrupt:
                print("\n[!] Dừng chế độ theo dõi.")
                break
            except Exception as e:
                print(f"[!] Lỗi trong quá trình quét: {e}")
                time.sleep(5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SubAI PC Google Drive Uploader")
    parser.add_argument("--cs", default="client_secret.json", help="Đường dẫn file client_secret.json")
    parser.add_argument("--watch", help="Theo dõi thư mục xuất video của SubAI")
    parser.add_argument("--upload", help="Upload 1 video cụ thể")
    parser.add_argument("--caption", default="", help="Mô tả / Tiêu đề video")
    parser.add_argument("--hashtags", default="#trending #xuhuong #subai", help="Hashtags cho video")
    parser.add_argument("--target", default="clone1", help="App TikTok Clone mục tiêu (clone1, clone2...)")
    parser.add_argument("--time", default="", help="Thời gian gợi ý đăng (vd: 18:30)")

    args = parser.parse_args()

    uploader = GoogleDriveUploader(client_secret_path=args.cs)

    if args.upload:
        uploader.upload_subai_video(
            video_path=args.upload,
            caption=args.caption or os.path.splitext(os.path.basename(args.upload))[0],
            hashtags=args.hashtags,
            target_clone=args.target,
            schedule_time=args.time
        )
    elif args.watch:
        uploader.watch_folder(args.watch, target_clone=args.target)
    else:
        print("\nSử dụng:")
        print("  python gdrive_uploader.py                              (Kích hoạt xác thực Google 1 lần)")
        print("  python gdrive_uploader.py --upload video.mp4           (Upload 1 video)")
        print("  python gdrive_uploader.py --watch ./exports            (Theo dõi thư mục SubAI tự động đẩy)")
