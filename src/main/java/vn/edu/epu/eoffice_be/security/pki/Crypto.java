package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Lớp tiện ích cung cấp các thao tác mật mã học (Cryptography Utilities):
 * - Đăng ký Security Provider BouncyCastle.
 * - Mã hóa/giải mã định dạng keystore PKCS#12 trong bộ nhớ.
 * - Chuyển đổi chứng thư số định dạng PEM trong bộ nhớ.
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

    /** In-memory PKCS12 operations for encrypted database storage. */
    public static byte[] encodeP12(char[] password, String alias, PrivateKey privateKey, Certificate[] chain) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            store.load(null, null);
            store.setKeyEntry(alias, privateKey, password, chain);
            try (var output = new java.io.ByteArrayOutputStream()) {
                store.store(output, password);
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot encode PKCS12 keystore");
        }
    }

    public static P12Bundle decodeP12(byte[] bytes, char[] password) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Empty PKCS12 keystore");
        try (var input = new java.io.ByteArrayInputStream(bytes)) {
            KeyStore store = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            store.load(input, password);
            var aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isKeyEntry(alias) && store.getKey(alias, password) instanceof PrivateKey key) {
                    Certificate[] raw = store.getCertificateChain(alias);
                    if (raw == null || raw.length == 0) throw new IllegalArgumentException();
                    X509Certificate[] chain = new X509Certificate[raw.length];
                    for (int i = 0; i < raw.length; i++) chain[i] = (X509Certificate) raw[i];
                    return new P12Bundle(key, chain);
                }
            }
            throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot decode PKCS12 keystore");
        }
    }

    public static String encodeCertPem(X509Certificate certificate) {
        try (var text = new java.io.StringWriter(); var writer = new JcaPEMWriter(text)) {
            writer.writeObject(certificate);
            writer.flush();
            return text.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot encode X509 certificate");
        }
    }

    public static X509Certificate decodeCertPem(String pem) {
        try (var input = new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME)
                    .generateCertificate(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot decode X509 certificate");
        }
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
     * sau khi được đọc từ dữ liệu keystore PKCS#12 trong bộ nhớ.
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
