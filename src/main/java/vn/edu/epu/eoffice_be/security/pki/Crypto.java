package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.encoders.Hex;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Lớp tiện ích cung cấp các thao tác mật mã học (Cryptography Utilities):
 * - Đăng ký Security Provider BouncyCastle.
 * - Đọc/Ghi Keystore định dạng PKCS#12 (.p12).
 * - Đọc/Ghi chứng thư số định dạng PEM (.crt/.cer).
 * - Tính toán băm mật mã SHA-256.
 */
public final class Crypto {

    // Đảm bảo BouncyCastle Provider chỉ được đăng ký duy nhất 1 lần vào Java Security Architecture
    static {
        registerBouncyCastle();
    }

    private Crypto() {
        // Lớp tiện ích tĩnh
    }

    /**
     * Đăng ký BouncyCastleProvider vào JVM nếu chưa tồn tại.
     */
    public static synchronized void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Đối tượng chứa cặp PrivateKey và chuỗi chứng thư số (Certificate Chain)
     * sau khi được giải mã từ file Keystore PKCS#12.
     */
    public record P12Bundle(PrivateKey privateKey, X509Certificate[] chain) {
        public P12Bundle {
            if (privateKey == null) {
                throw new IllegalArgumentException("PrivateKey không được phép null");
            }
            if (chain == null || chain.length == 0) {
                throw new IllegalArgumentException("Chuỗi chứng thư (certificate chain) không được rỗng");
            }
        }

        /**
         * Lấy chứng thư số của chính chủ thể (leaf / end-entity certificate).
         */
        public X509Certificate getEndEntityCertificate() {
            return chain[0];
        }

        @Override
        public String toString() {
            return "P12Bundle[privateKey=[PROTECTED], chainLength=" + (chain != null ? chain.length : 0) + "]";
        }
    }

    /**
     * Nạp khóa bí mật (PrivateKey) và chuỗi chứng thư từ file Keystore PKCS#12.
     *
     * @param path     Đường dẫn file .p12
     * @param password Mật khẩu giải mã Keystore (dưới dạng mảng char)
     * @return P12Bundle chứa PrivateKey và mảng X509Certificate[]
     */
    public static P12Bundle loadP12(Path path, char[] password) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("File PKCS#12 không tồn tại: " + (path != null ? path.toAbsolutePath() : "null"));
        }

        try {
            // Khởi tạo Keystore với chuẩn PKCS12 thông qua BouncyCastle Provider
            KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            try (InputStream is = Files.newInputStream(path)) {
                keyStore.load(is, password);
            }

            // Tìm alias đầu tiên chứa khóa ký
            Enumeration<String> aliases = keyStore.aliases();
            String targetAlias = null;
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    targetAlias = alias;
                    break;
                }
            }

            if (targetAlias == null) {
                throw new IllegalStateException("Keystore không chứa bất kỳ mục khóa riêng (Key Entry) nào: " + path.getFileName());
            }

            // Trích xuất PrivateKey với mật khẩu tương ứng
            Key key = keyStore.getKey(targetAlias, password);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("Mục '" + targetAlias + "' trong Keystore không phải là PrivateKey hợp lệ");
            }

            // Trích xuất chuỗi chứng thư (Certificate Chain)
            Certificate[] rawChain = keyStore.getCertificateChain(targetAlias);
            if (rawChain == null || rawChain.length == 0) {
                throw new IllegalStateException("Không tìm thấy chuỗi chứng thư số liên kết với alias: " + targetAlias);
            }

            // Chuyển đổi sang chuẩn X509Certificate[]
            X509Certificate[] x509Chain = new X509Certificate[rawChain.length];
            for (int i = 0; i < rawChain.length; i++) {
                if (!(rawChain[i] instanceof X509Certificate x509Cert)) {
                    throw new IllegalStateException("Chứng thư thứ " + i + " trong chuỗi không phải chuẩn X.509");
                }
                x509Chain[i] = x509Cert;
            }

            return new P12Bundle(privateKey, x509Chain);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi nạp file PKCS#12 (" + path.toAbsolutePath() + "): " + e.getMessage(), e);
        }
    }

    /**
     * Nạp file PKCS#12 với mật khẩu dạng String.
     */
    public static P12Bundle loadP12(Path path, String password) {
        return loadP12(path, password != null ? password.toCharArray() : new char[0]);
    }

    /**
     * Lưu PrivateKey và chuỗi chứng thư vào file Keystore PKCS#12 chuẩn.
     *
     * @param path       Đường dẫn file đích cần lưu (.p12)
     * @param password   Mật khẩu bảo vệ Keystore và khóa riêng
     * @param alias      Tên định danh (alias) của khóa
     * @param privateKey Khóa riêng cần bảo vệ
     * @param chain      Chuỗi chứng thư số tương ứng (theo thứ tự: Leaf cert -> Intermediate -> Root CA)
     */
    public static void saveP12(Path path, char[] password, String alias, PrivateKey privateKey, Certificate[] chain) {
        if (path == null) {
            throw new IllegalArgumentException("Đường dẫn file lưu PKCS#12 không được null");
        }
        if (privateKey == null || chain == null || chain.length == 0) {
            throw new IllegalArgumentException("PrivateKey và Certificate Chain không được để trống khi lưu Keystore");
        }

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            keyStore.load(null, null); // Khởi tạo Keystore mới trong bộ nhớ

            // Đặt khóa riêng kèm theo chuỗi chứng thư vào keystore
            keyStore.setKeyEntry(alias, privateKey, password, chain);

            try (OutputStream os = Files.newOutputStream(path)) {
                keyStore.store(os, password);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi lưu file PKCS#12 vào " + path.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Lưu file PKCS#12 với mật khẩu dạng String.
     */
    public static void saveP12(Path path, String password, String alias, PrivateKey privateKey, Certificate[] chain) {
        saveP12(path, password != null ? password.toCharArray() : new char[0], alias, privateKey, chain);
    }

    /**
     * Lưu chứng thư số X.509 dưới dạng văn bản PEM (Base64 ASCII có header BEGIN/END CERTIFICATE).
     *
     * @param path Đường dẫn file PEM cần ghi (.crt hoặc .pem)
     * @param cert Chứng thư số X509Certificate
     */
    public static void saveCertPem(Path path, X509Certificate cert) {
        if (path == null || cert == null) {
            throw new IllegalArgumentException("Đường dẫn lưu PEM và chứng thư số không được null");
        }

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (JcaPEMWriter pemWriter = new JcaPEMWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
                pemWriter.writeObject(cert);
                pemWriter.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi xuất chứng thư PEM ra file " + path.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Đọc chứng thư số X.509 từ file PEM (.crt hoặc .pem).
     *
     * @param path Đường dẫn file PEM chứa chứng thư số
     * @return Đối tượng X509Certificate
     */
    public static X509Certificate loadCertPem(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("File chứng thư PEM không tồn tại: " + (path != null ? path.toAbsolutePath() : "null"));
        }

        try {
            // Ưu tiên đọc bằng BouncyCastle PEMParser
            try (PEMParser pemParser = new PEMParser(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
                Object parsedObject = pemParser.readObject();

                if (parsedObject instanceof X509CertificateHolder holder) {
                    return new JcaX509CertificateConverter()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .getCertificate(holder);
                }

                if (parsedObject instanceof X509Certificate x509Cert) {
                    return x509Cert;
                }
            }

            // Fallback: Sử dụng Java CertificateFactory chuẩn X.509
            try (InputStream is = Files.newInputStream(path)) {
                CertificateFactory factory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
                Certificate certificate = factory.generateCertificate(is);
                if (certificate instanceof X509Certificate x509) {
                    return x509;
                }
            }

            throw new IllegalStateException("Nội dung trong file không phải chứng thư X.509 hợp lệ: " + path.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đọc chứng thư PEM từ " + path.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Tính toán giá trị băm mật mã SHA-256 của khối dữ liệu byte[]
     * và chuyển đổi thành chuỗi Hex viết thường.
     *
     * @param data Dữ liệu đầu vào cần băm
     * @return Chuỗi Hexadecimal đại diện cho giá trị băm 256-bit (64 ký tự hex)
     */
    public static String sha256Hex(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Dữ liệu đầu vào để tính băm SHA-256 không được null");
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256", BouncyCastleProvider.PROVIDER_NAME);
            byte[] digest = md.digest(data);
            return Hex.toHexString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tính toán hàm băm SHA-256: " + e.getMessage(), e);
        }
    }
}
