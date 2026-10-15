# ==============================================================================
# SCRIPT KHOI TAO ROOT CA AN TOAN (BAO VE CHONG GHI DE & BAO MAT KHOA)
# Du an: Quan ly Van phong Dien tu (e-Office EPU)
# ==============================================================================

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

# Kiem tra OpenSSL co trong PATH hay chua
if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    Write-Host "[LOI] Khong tim thay lenh 'openssl' trong PATH cua he thong!" -ForegroundColor Red
    Write-Host "Vui long cai dat OpenSSL hoac them OpenSSL vao bien moi truong PATH." -ForegroundColor Yellow
    exit 1
}

# Xac dinh duong dan thu muc goc du an eoffice-be
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BaseDir   = Split-Path -Parent $ScriptDir
$CaDir     = Join-Path $BaseDir "uploads/keystores/ca"
$UsersDir  = Join-Path $BaseDir "uploads/keystores/users"

$CaP12Path   = Join-Path $CaDir "ca.p12"
$CaCertPath  = Join-Path $CaDir "ca.crt"
$RevokedPath = Join-Path $CaDir "revoked.json"

# Mat khau co dinh dong bo voi backend Java (changeit)
$Password = "changeit"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "    KIEM TRA CHOT CHAN AN TOAN TRUOC KHI KHOI TAO CA    " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# CHOT CHAN 1: Kiem tra file ca.p12 da ton tai chua?
if (Test-Path -Path $CaP12Path) {
    Write-Host ""
    Write-Host "[CHAN] FILE ROOT CA DA TON TAI TAI:" -ForegroundColor Red
    Write-Host "       $CaP12Path" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "NGUYEN TAC AN TOAN: Tuyet doi khong duoc ghi de Root CA!" -ForegroundColor Red
    Write-Host "Neu ghi de, toan bo van ban va chu ky so truoc day se BI HONG." -ForegroundColor Yellow
    Write-Host "Dung script ngay lap tuc. Khong co thay doi nao duoc thuc hien." -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Cyan
    exit 1
}

# CHOT CHAN 2: Kiem tra da co chung thu user nao chua?
if (Test-Path -Path $UsersDir) {
    $userCerts = Get-ChildItem -Path $UsersDir -Filter "user_*.p12" -ErrorAction SilentlyContinue
    if ($userCerts -and $userCerts.Count -gt 0) {
        Write-Host ""
        Write-Host "[CHAN] DA TON TAI $($userCerts.Count) CHUNG THU CUA NGUOI DUNG!" -ForegroundColor Red
        Write-Host "       Thu muc: $UsersDir" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "CANH BAO: Da co user_*.p12, ban PHAI khoi phuc ca.p12 tu BACKUP." -ForegroundColor Red
        Write-Host "Nghiem cam tao moi Root CA vi se lam lech chuoi tin cay PKI." -ForegroundColor Yellow
        Write-Host "========================================================" -ForegroundColor Cyan
        exit 1
    }
}

Write-Host "[HOP LE] Du dieu kien khoi tao Root CA moi." -ForegroundColor Green

# 1. Tao thu muc neu chua co
if (-not (Test-Path -Path $CaDir)) {
    New-Item -ItemType Directory -Path $CaDir -Force | Out-Null
}
if (-not (Test-Path -Path $UsersDir)) {
    New-Item -ItemType Directory -Path $UsersDir -Force | Out-Null
}

$TmpKey = Join-Path $CaDir "ca.key.tmp"
$TmpCrt = Join-Path $CaDir "ca.crt.tmp"
$TmpP12 = Join-Path $CaDir "ca.p12.tmp"

# Dua password vao bien moi truong tam thoi cho OpenSSL
$env:OPENSSL_CA_PASS = $Password

try {
    # 2. Sinh cap khoa RSA 3072-bit va chung thu X.509 v3 (Han 10 nam = 3650 ngay)
    Write-Host "-> Dang sinh khoa RSA 3072-bit va chung thu tu ky Root CA..." -ForegroundColor Gray
    openssl req -x509 -newkey rsa:3072 -nodes `
        -keyout $TmpKey `
        -out $TmpCrt `
        -days 3650 `
        -subj "/C=VN/O=Truong Dai hoc Dien Luc - EPU/CN=E-Office Demo Root CA" `
        -addext "basicConstraints=critical,CA:TRUE,pathlen:0" `
        -addext "keyUsage=critical,keyCertSign,cRLSign"
    
    # Bat loi $LASTEXITCODE cua lenh openssl req
    if ($LASTEXITCODE -ne 0) {
        throw "Lenh 'openssl req' that bai voi ma loi exit code: $LASTEXITCODE"
    }

    # 3. Dong goi PKCS#12
    Write-Host "-> Dang dong goi file PKCS#12 (ca.p12)..." -ForegroundColor Gray
    openssl pkcs12 -export `
        -name "eoffice-root-ca" `
        -out $TmpP12 `
        -inkey $TmpKey `
        -in $TmpCrt `
        -passout env:OPENSSL_CA_PASS

    # Bat loi $LASTEXITCODE cua lenh openssl pkcs12
    if ($LASTEXITCODE -ne 0) {
        throw "Lenh 'openssl pkcs12' that bai voi ma loi exit code: $LASTEXITCODE"
    }

    # BO -Force o Move-Item de filesystem bao loi neu file dich xuat hien giua chung
    Move-Item -Path $TmpP12 -Destination $CaP12Path
    Move-Item -Path $TmpCrt -Destination $CaCertPath

    # Khoi tao danh sach thu hoi rong
    if (-not (Test-Path -Path $RevokedPath)) {
        "[]" | Set-Content -Path $RevokedPath -Encoding UTF8
    }

    $ExpireDate = (Get-Date).AddDays(3650).ToString("dd/MM/yyyy")

    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host "     KHOI TAO ROOT CA THANH CONG VA AN TOAN TUYET DOI!  " -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host "-> File Keystore:  $CaP12Path" -ForegroundColor White
    Write-Host "-> File Cert cong: $CaCertPath" -ForegroundColor White
    Write-Host "-> Alias khoa:     eoffice-root-ca" -ForegroundColor White
    Write-Host "-> Han su dung:    10 nam (Het han vao ngay: $ExpireDate)" -ForegroundColor Cyan
    Write-Host "-> Mat khau CA:    changeit (Co dinh dong bo voi backend Java)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "[QUAN TRONG - VAN HANH]:" -ForegroundColor Red
    Write-Host "1. Hay BACKUP file 'ca.p12' nay ngay vao noi an toan (mat la khong khoi phuc duoc)." -ForegroundColor Yellow
    Write-Host "2. Khong bao gio chay lenh tao lai CA khi he thong da di vao van hanh." -ForegroundColor Yellow
    Write-Host "========================================================" -ForegroundColor Green
}
catch {
    Write-Host ""
    Write-Host "[THAT BAI] Qua trinh tao Root CA gap loi: $_" -ForegroundColor Red
    exit 1
}
finally {
    # Xoa mat khau khoi bien moi truong tam thoi
    $env:OPENSSL_CA_PASS = $null

    # Luon luon don dep file private key tran du thanh cong hay that bai
    if (Test-Path -Path $TmpKey) {
        Remove-Item -Path $TmpKey -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -Path $TmpCrt) {
        Remove-Item -Path $TmpCrt -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -Path $TmpP12) {
        Remove-Item -Path $TmpP12 -Force -ErrorAction SilentlyContinue
    }
}
