# CFM Manager

Android root manager for **Clash for Magisk v3.0**, ưu tiên Android 11 / MIUI 12.5.

## Tính năng

- START / STOP / RESTART Clash và transparent proxy bằng script của module.
- Dashboard trạng thái core, PID, traffic, proxy/network mode.
- Clash REST API: Rule / Global / Direct, proxy groups và đổi node.
- Subscription manager.
- Blacklist / whitelist app qua `/data/clash/packages.list`.
- TUN / TProxy / REDIRECT, IPv6, MTU và TUN stack.
- Log và backup/restore config.
- Xem/chặn client hotspot cơ bản.
- Tự kiểm tra bản cập nhật từ GitHub khi mở app; chỉ tải/cài khi người dùng đồng ý.

## Auto Update

App mặc định kiểm tra:

`https://raw.githubusercontent.com/lemanhquyen20004/CFM-Manager/main/update.json`

APK chính thức được phát hành tại GitHub Releases. Android Package Installer vẫn yêu cầu xác nhận của người dùng khi cập nhật.

## Build

- JDK 17
- Android Gradle Plugin 8.6.1
- compileSdk 35
- minSdk 26
- targetSdk 30

Mở project bằng Android Studio hoặc dùng workflow `.github/workflows/release.yml`.

## Release

Xem [`docs/RELEASE.md`](docs/RELEASE.md).
