# KasirHub POS Android-Only v2.1.0

## Konsep
KasirHub sekarang **sepenuhnya Android**. Tidak ada WordPress dan tidak membutuhkan server cloud untuk operasional harian.

Satu HP/tablet Android dijadikan **Local Server / Local Hub**. HP/tablet lain menjadi Kasir, Dapur, Mini Bar, Souvenir, Manager, atau Customer.

Semua perangkat berada di hotspot/Wi-Fi lokal yang sama.

## Fitur
- Local database SQLite di HP Server
- Order meja via QR
- Nama meja + kode meja
- Customer mode dengan katalog 2 kolom, gambar produk, kategori, pencarian, cart, nama dan nomor WhatsApp
- Tracking order realtime via polling lokal
- QRIS / transfer / bayar di kasir sebagai metode pencatatan lokal
- Kasir melihat order dan konfirmasi lunas
- Dapur, Mini Bar, Souvenir menerima item sesuai station
- Alarm station dan alarm kasir berulang sampai order dilihat
- Auto-print thermal via LAN/Wi-Fi TCP 9100 atau Bluetooth SPP
- Tambah produk + upload gambar ke HP Server
- Tambah meja manual + QR otomatis
- Dashboard Manager lokal
- Laporan dan export CSV yang dapat dibuka di Excel
- Reservasi dasar
- Deep link QR: `kasirhub://order?table=M01&hub=IP:8787`
- Auto-start service setelah boot

## Default login
- manager / 123456
- kasir / 123456
- dapur / 123456
- bar / 123456
- souvenir / 123456

Ganti password untuk penggunaan produksi.

## Cara penggunaan
### HP Server
Install APK -> pilih `MODE: Local Server (HP Server)`, klik **Simpan & Jalankan**.
Server menampilkan URL lokal seperti `http://192.168.4.1:8787`.

### Station
Install APK -> pilih `MODE: Station / Client`, masukkan URL Local Server, lalu pilih station.

### Customer
Scan QR meja untuk membuka deep link atau buka `/order?table=M01` pada browser lokal.

### Printer
LAN/Wi-Fi: IP printer dan port 9100.
Bluetooth: MAC address printer.

## Offline
Selama hotspot/router lokal dan HP Server hidup, order, pembayaran tunai/bayar di kasir, status, alarm, print, katalog, meja dan laporan tetap berjalan tanpa internet.
QRIS gateway online dan WhatsApp membutuhkan internet.
