# Phát hành CFM Manager

Repo chính: `https://github.com/lemanhquyen20004/CFM-Manager`

Manifest cập nhật mà app đọc mặc định:

`https://raw.githubusercontent.com/lemanhquyen20004/CFM-Manager/main/update.json`

## 1. Thiết lập ký APK một lần

GitHub repository -> **Settings -> Secrets and variables -> Actions -> New repository secret**.

Tạo 4 secrets:

- `CFM_KEYSTORE_BASE64`: nội dung file keystore đã chuyển sang Base64.
- `CFM_KEYSTORE_PASSWORD`: mật khẩu keystore.
- `CFM_KEY_ALIAS`: alias của key.
- `CFM_KEY_PASSWORD`: mật khẩu key.

Giữ keystore và mật khẩu ở nơi an toàn. Mọi APK cập nhật phải được ký bằng đúng key này, nếu không Android sẽ không cho cập nhật đè lên bản cũ.

## 2. Mỗi lần ra bản mới

Ví dụ phát hành `v1.2.0`:

1. Trong `app/build.gradle`, tăng:
   - `versionCode 3`
   - `versionName '1.2.0'`
2. Sửa `release-notes.txt` thành danh sách thay đổi của bản mới.
3. Commit và push lên `main`.
4. Tạo tag đúng với versionName rồi push:

```bash
git tag v1.2.0
git push origin v1.2.0
```

GitHub Actions sẽ tự:

1. Build APK release đã ký.
2. Tạo `CFM-Manager-v1.2.0.apk`.
3. Tạo SHA-256.
4. Tạo GitHub Release `v1.2.0`.
5. Upload APK và file `.sha256` vào Release.
6. Cập nhật `update.json` trên nhánh `main`.

Khi người dùng mở app, CFM Manager sẽ đọc `update.json`. Nếu `versionCode` mới hơn bản đang cài, app sẽ hiện hộp thoại hỏi cập nhật.

## Quy tắc quan trọng

- `versionCode` phải tăng ở mọi bản phát hành.
- Tag phải đúng dạng `v` + `versionName`, ví dụ `v1.2.0`.
- Không thay signing keystore sau khi đã phát hành bản đầu tiên.
- Không commit `app/release.keystore`, `.jks`, `.keystore` lên repo.
