#!/usr/bin/env bash
# ==============================================================================
# SCRIPT KHỞI TẠO ROOT CA AN TOÀN (BẢO VỆ CHỐNG GHI ĐÈ & BẢO MẬT KHÓA)
# Dự án: Quản lý Văn phòng Điện tử (e-Office EPU)
# ==============================================================================

set -eo pipefail

# 1. Kiểm tra OpenSSL có trong PATH hay chưa
if ! command -v openssl &> /dev/null; then
    echo -e "\033[0;31m[LỖI] Không tìm thấy lệnh 'openssl' trong PATH của hệ thống!\033[0m"
    echo -e "\033[0;33mVui lòng cài đặt OpenSSL (apt install openssl / yum install openssl).\033[0m"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
CA_DIR="$BASE_DIR/uploads/keystores/ca"
USERS_DIR="$BASE_DIR/uploads/keystores/users"

CA_P12_PATH="$CA_DIR/ca.p12"
CA_CERT_PATH="$CA_DIR/ca.crt"
REVOKED_PATH="$CA_DIR/revoked.json"
PASSWORD="changeit"

echo -e "\033[0;36m========================================================\033[0m"
echo -e "\033[0;36m    KIỂM TRA CHỐT CHẶN AN TOÀN TRƯỚC KHI KHỞI TẠO CA    \033[0m"
echo -e "\033[0;36m========================================================\033[0m"

# CHỐT CHẶN 1: Kiểm tra file ca.p12 đã tồn tại chưa?
if [ -f "$CA_P12_PATH" ]; then
    echo ""
    echo -e "\033[0;31m[CHẶN] FILE ROOT CA ĐÃ TỒN TẠI TẠI:\033[0m"
    echo -e "\033[0;33m       $CA_P12_PATH\033[0m"
    echo ""
    echo -e "\033[0;31mNGUYÊN TẮC AN TOÀN: Tuyệt đối không được ghi đè Root CA!\033[0m"
    echo -e "\033[0;33mNếu ghi đè, toàn bộ văn bản và chữ ký số trước đây sẽ BỊ HỎNG.\033[0m"
    echo -e "\033[0;32mDừng script ngay lập tức. Không có thay đổi nào được thực hiện.\033[0m"
    echo -e "\033[0;36m========================================================\033[0m"
    exit 1
fi

# CHỐT CHẶN 2: Kiểm tra đã có chứng thư user nào chưa?
if [ -d "$USERS_DIR" ]; then
    USER_CERTS_COUNT=$(find "$USERS_DIR" -maxdepth 1 -name "user_*.p12" 2>/dev/null | wc -l)
    if [ "$USER_CERTS_COUNT" -gt 0 ]; then
        echo ""
        echo -e "\033[0;31m[CHẶN] ĐÃ TỒN TẠI $USER_CERTS_COUNT CHỨNG THƯ CỦA NGƯỜI DÙNG!\033[0m"
        echo -e "\033[0;33m       Thư mục: $USERS_DIR\033[0m"
        echo ""
        echo -e "\033[0;31mCẢNH BÁO: Đã có user_*.p12, bạn PHẢI khôi phục ca.p12 từ BACKUP.\033[0m"
        echo -e "\033[0;33mNghiêm cấm tạo mới Root CA vì sẽ làm lệch chuỗi tin cậy PKI.\033[0m"
        echo -e "\033[0;36m========================================================\033[0m"
        exit 1
    fi
fi

echo -e "\033[0;32m[HỢP LỆ] Đủ điều kiện khởi tạo Root CA mới.\033[0m"

mkdir -p "$CA_DIR"
mkdir -p "$USERS_DIR"

TMP_KEY="$CA_DIR/ca.key.tmp"
TMP_CRT="$CA_DIR/ca.crt.tmp"
TMP_P12="$CA_DIR/ca.p12.tmp"

cleanup() {
    export OPENSSL_CA_PASS=""
    rm -f "$TMP_KEY" "$TMP_CRT" "$TMP_P12"
}
trap cleanup EXIT

export OPENSSL_CA_PASS="$PASSWORD"

echo -e "\033[0;90m-> Đang sinh khóa RSA 3072-bit và chứng thư tự ký Root CA...\033[0m"
openssl req -x509 -newkey rsa:3072 -nodes \
    -keyout "$TMP_KEY" \
    -out "$TMP_CRT" \
    -days 3650 \
    -subj "/C=VN/O=Truong Dai hoc Dien Luc - EPU/CN=E-Office Demo Root CA" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"

if [ $? -ne 0 ]; then
    echo -e "\033[0;31m[LỖI] Lệnh 'openssl req' thất bại!\033[0m"
    exit 1
fi

echo -e "\033[0;90m-> Đang đóng gói file PKCS#12 (ca.p12)...\033[0m"
openssl pkcs12 -export \
    -name "eoffice-root-ca" \
    -out "$TMP_P12" \
    -inkey "$TMP_KEY" \
    -in "$TMP_CRT" \
    -passout env:OPENSSL_CA_PASS

if [ $? -ne 0 ]; then
    echo -e "\033[0;31m[LỖI] Lệnh 'openssl pkcs12' thất bại!\033[0m"
    exit 1
fi

if [ -f "$CA_P12_PATH" ] || [ -f "$CA_CERT_PATH" ]; then
    echo -e "\033[0;31m[LỖI] File đích bất ngờ xuất hiện trước khi di chuyển! Hủy thao tác.\033[0m"
    exit 1
fi

mv "$TMP_P12" "$CA_P12_PATH"
mv "$TMP_CRT" "$CA_CERT_PATH"

if [ ! -f "$REVOKED_PATH" ]; then
    echo "[]" > "$REVOKED_PATH"
fi

echo ""
echo -e "\033[0;32m========================================================\033[0m"
echo -e "\033[0;32m     KHỞI TẠO ROOT CA THÀNH CÔNG VÀ AN TOÀN TUYỆT ĐỐI!  \033[0m"
echo -e "\033[0;32m========================================================\033[0m"
echo -e "-> File Keystore:  $CA_P12_PATH"
echo -e "-> File Cert công: $CA_CERT_PATH"
echo -e "-> Alias khóa:     eoffice-root-ca"
echo -e "-> Hạn sử dụng:    10 năm"
echo -e "\033[0;33m-> Mật khẩu CA:    changeit (Cố định đồng bộ với backend Java)\033[0m"
echo ""
echo -e "\033[0;31m[QUAN TRỌNG - VẬN HÀNH]:\033[0m"
echo -e "\033[0;33m1. Hãy BACKUP file 'ca.p12' này ngay vào nơi an toàn.\033[0m"
echo -e "\033[0;33m2. Không bao giờ chạy lệnh tạo lại CA khi hệ thống đã đi vào vận hành.\033[0m"
echo -e "\033[0;32m========================================================\033[0m"
