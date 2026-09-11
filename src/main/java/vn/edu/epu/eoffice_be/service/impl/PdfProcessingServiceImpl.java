package vn.edu.epu.eoffice_be.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.service.PdfProcessingService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfProcessingServiceImpl implements PdfProcessingService {

    @Value("${eoffice.pki.keystore-path:}")
    private String keystorePath;

    @Value("${eoffice.pki.keystore-password:}")
    private String keystorePassword;

    @Value("${eoffice.pki.key-alias:}")
    private String keyAlias;

    @Value("${eoffice.pki.key-password:}")
    private String keyPassword;

    @Value("${eoffice.pki.fallback-keystore-path:./uploads/keystore/epu-pki.p12}")
    private String fallbackKeystorePath = "./uploads/keystore/epu-pki.p12";

    private PrivateKey epuPrivateKey;
    private X509Certificate epuCertificate;

    public PdfProcessingServiceImpl() {
        initProvider();
        init();
    }

    public PdfProcessingServiceImpl(PrivateKey privateKey, X509Certificate certificate) {
        initProvider();
        this.epuPrivateKey = privateKey;
        this.epuCertificate = certificate;
    }

    private static void initProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @PostConstruct
    public synchronized void init() {
        initProvider();
        if (keystorePath != null && !keystorePath.isBlank()) {
            loadFromConfiguredKeystore(keystorePath, keystorePassword, keyAlias, keyPassword);
            return;
        }
        if (this.epuPrivateKey != null && this.epuCertificate != null) {
            return;
        }
        loadOrInitializeKeys();
    }

    private void loadOrInitializeKeys() {
        if (keystorePath != null && !keystorePath.isBlank()) {
            loadFromConfiguredKeystore(keystorePath, keystorePassword, keyAlias, keyPassword);
            return;
        }

        // TICKET-PKI-PROD: Keystore fallback - Persist key/cert locally so it is not regenerated on every startup
        Path fallbackPath = Paths.get(fallbackKeystorePath != null && !fallbackKeystorePath.isBlank()
                ? fallbackKeystorePath
                : "./uploads/keystore/epu-pki.p12").toAbsolutePath().normalize();

        if (Files.exists(fallbackPath)) {
            try {
                loadFromKeystoreFile(fallbackPath, "epu-keystore-secret", "epu-signer", "epu-signer-secret");
                log.info("Tai chung thu so PKI thanh cong tu keystore da luu tru: {}", fallbackPath);
                return;
            } catch (Exception e) {
                log.warn("Khong the doc keystore da ton tai tai {}. Se tien hanh tao moi va ghi de.", fallbackPath, e);
            }
        }

        // Generate and persist once to disk
        log.warn("CANH BAO [TICKET-PKI-PROD]: Chua cau hinh eoffice.pki.keystore-path. Dang sinh va luu keystore tu sinh tai: {}. Doi voi moi truong Production, bat buoc phai cau hinh chung thu so PKI tu CA chinh thuc!", fallbackPath);
        generateAndPersistKeystore(fallbackPath, "epu-keystore-secret", "epu-signer", "epu-signer-secret");
    }

    private void loadFromConfiguredKeystore(String pathStr, String storePwd, String alias, String keyPwd) {
        try {
            InputStream is;
            if (pathStr.startsWith("classpath:")) {
                String resourcePath = pathStr.substring("classpath:".length());
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }
                is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (is == null) {
                    throw new FileNotFoundException("Khong tim thay keystore tren classpath: " + pathStr);
                }
            } else {
                Path p = Paths.get(pathStr);
                if (!Files.exists(p)) {
                    throw new FileNotFoundException("Khong tim thay keystore tai: " + pathStr);
                }
                is = Files.newInputStream(p);
            }

            try (is) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                char[] password = storePwd != null ? storePwd.toCharArray() : new char[0];
                keyStore.load(is, password);

                String keyAliasToUse = alias;
                if (keyAliasToUse == null || keyAliasToUse.isBlank()) {
                    Enumeration<String> aliases = keyStore.aliases();
                    if (aliases.hasMoreElements()) {
                        keyAliasToUse = aliases.nextElement();
                    } else {
                        throw new IllegalStateException("Keystore khong chua bat ky alias nao: " + pathStr);
                    }
                }

                char[] keyPasswordToUse = (keyPwd != null && !keyPwd.isBlank()) ? keyPwd.toCharArray() : password;
                Key key = keyStore.getKey(keyAliasToUse, keyPasswordToUse);
                if (!(key instanceof PrivateKey)) {
                    throw new IllegalStateException("Khoa duoi alias '" + keyAliasToUse + "' khong phai la PrivateKey");
                }
                Certificate cert = keyStore.getCertificate(keyAliasToUse);
                if (!(cert instanceof X509Certificate)) {
                    throw new IllegalStateException("Chung chi duoi alias '" + keyAliasToUse + "' khong phai la X509Certificate");
                }

                this.epuPrivateKey = (PrivateKey) key;
                this.epuCertificate = (X509Certificate) cert;
                log.info("Tai chung thu so PKI tu cau hinh keystore thanh cong: alias={}", keyAliasToUse);
            }
        } catch (Exception e) {
            log.error("Loi khi tai keystore PKI tu {}", pathStr, e);
            throw new IllegalStateException("Khong the khoi tao dich vu PKI tu keystore: " + pathStr, e);
        }
    }

    private void loadFromKeystoreFile(Path path, String storePwd, String alias, String keyPwd) throws Exception {
        try (InputStream is = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = storePwd != null ? storePwd.toCharArray() : new char[0];
            keyStore.load(is, password);

            String keyAliasToUse = alias;
            if (keyAliasToUse == null || keyAliasToUse.isBlank() || !keyStore.containsAlias(keyAliasToUse)) {
                Enumeration<String> aliases = keyStore.aliases();
                if (aliases.hasMoreElements()) {
                    keyAliasToUse = aliases.nextElement();
                }
            }

            char[] keyPasswordToUse = (keyPwd != null && !keyPwd.isBlank()) ? keyPwd.toCharArray() : password;
            Key key = keyStore.getKey(keyAliasToUse, keyPasswordToUse);
            Certificate cert = keyStore.getCertificate(keyAliasToUse);

            if (!(key instanceof PrivateKey) || !(cert instanceof X509Certificate)) {
                throw new IllegalStateException("Keystore tai " + path + " khong chua PrivateKey hoac X509Certificate hop le");
            }

            this.epuPrivateKey = (PrivateKey) key;
            this.epuCertificate = (X509Certificate) cert;
        }
    }

    private void generateAndPersistKeystore(Path path, String storePwd, String alias, String keyPwd) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            PrivateKey privKey = keyPair.getPrivate();

            Instant now = Instant.now();
            Date startDate = Date.from(now.minus(Duration.ofHours(1)));
            Date endDate = Date.from(now.plus(Duration.ofDays(365 * 5)));
            BigInteger serialNumber = BigInteger.valueOf(now.toEpochMilli());
            X500Name dnName = new X500Name("CN=Truong Dai hoc Dien Luc - EPU, OU=Ban Giam Hieu, O=EPU, C=VN");

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName, serialNumber, startDate, endDate, dnName, keyPair.getPublic());
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privKey);
            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(certHolder);

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            char[] password = storePwd != null ? storePwd.toCharArray() : new char[0];
            char[] keyPassword = (keyPwd != null && !keyPwd.isBlank()) ? keyPwd.toCharArray() : password;
            keyStore.setKeyEntry(alias, privKey, keyPassword, new Certificate[]{cert});

            try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                keyStore.store(os, password);
            }

            this.epuPrivateKey = privKey;
            this.epuCertificate = cert;
            log.info("Luu keystore PKCS12 thanh cong vao {}", path.toAbsolutePath());
        } catch (Exception e) {
            log.error("Loi khi sinh va luu keystore tai {}", path, e);
            throw new IllegalStateException("Khong the khoi tao va luu keystore", e);
        }
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.epuPrivateKey = privateKey;
    }

    public void setCertificate(X509Certificate certificate) {
        this.epuCertificate = certificate;
    }

    public PrivateKey getPrivateKey() {
        return this.epuPrivateKey;
    }

    public X509Certificate getCertificate() {
        return this.epuCertificate;
    }

    @Override
    public byte[] applyInitialSignStamp(byte[] pdfBytes, StampInfo stampInfo) {
        if (pdfBytes == null || pdfBytes.length == 0 || stampInfo == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }

            PDPage lastPage = document.getPage(totalPages - 1);
            PDRectangle mediaBox = lastPage.getMediaBox();

            float stampWidth = 190f;
            float stampHeight = 55f;
            float x = mediaBox.getWidth() - stampWidth - 30f;
            float y = 35f;

            try (PDPageContentStream cs = new PDPageContentStream(
                    document, lastPage, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // 1. Khung viền con dấu (xanh dương)
                cs.setStrokingColor(new Color(16, 76, 151));
                cs.setLineWidth(1.2f);
                cs.addRect(x, y, stampWidth, stampHeight);
                cs.stroke();

                // 2. Nền mờ nhẹ
                cs.setNonStrokingColor(new Color(240, 245, 255));
                cs.addRect(x + 1, y + 1, stampWidth - 2, stampHeight - 2);
                cs.fill();

                // 3. Tiêu đề
                cs.beginText();
                cs.setNonStrokingColor(new Color(16, 76, 151));
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8f);
                cs.newLineAtOffset(x + 8, y + stampHeight - 12);
                cs.showText("[ DAU KY NHAY DIEN TU - EPU ]");
                cs.endText();

                // 4. Họ tên người ký
                cs.beginText();
                cs.setNonStrokingColor(new Color(33, 37, 41));
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.5f);
                cs.newLineAtOffset(x + 8, y + stampHeight - 24);
                String safeName = toAscii("Can bo: " + (stampInfo.getSignerName() != null ? stampInfo.getSignerName() : ""));
                cs.showText(safeName);
                cs.endText();

                // 5. Chức vụ
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7f);
                cs.newLineAtOffset(x + 8, y + stampHeight - 35);
                String safePos = toAscii("Chuc vu: " + (stampInfo.getPositionTitle() != null ? stampInfo.getPositionTitle() : ""));
                cs.showText(safePos);
                cs.endText();

                // 6. Thời gian
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 6.5f);
                cs.newLineAtOffset(x + 8, y + stampHeight - 46);
                String safeDate = toAscii("Thoi gian: " + (stampInfo.getSignDate() != null ? stampInfo.getSignDate() : ""));
                cs.showText(safeDate);
                cs.endText();
            }

            document.save(baos);
            log.info("Nhung dau ky nhay thanh cong vao trang cuoi file PDF.");
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Loi khi xu ly dong dau ky nhay tren PDF", e);
            throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR);
        }
    }

    @Override
    public byte[] signAndCertifyPdf(byte[] pdfBytes, String signerName, String reason) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDSignature pdSignature = new PDSignature();
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            pdSignature.setName(toAscii(signerName != null ? signerName : "Truong Dai hoc Dien Luc"));
            pdSignature.setLocation("Ha Noi, Viet Nam");
            pdSignature.setReason(toAscii(reason != null ? reason : "Ky phat hanh van ban dien tu"));
            pdSignature.setSignDate(Calendar.getInstance());

            // Thiết lập cờ niêm phong CERTIFIED_NO_CHANGES_ALLOWED (Khóa cấm sửa đổi)
            setCertificationPermissions(document, pdSignature, 1);

            SignatureInterface signatureInterface = content -> {
                try {
                    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                    ContentSigner sha256Signer = new JcaContentSignerBuilder("SHA256WithRSA")
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(epuPrivateKey);

                    gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                            .build(sha256Signer, epuCertificate));

                    gen.addCertificate(new X509CertificateHolder(epuCertificate.getEncoded()));

                    byte[] contentBytes = content.readAllBytes();
                    CMSProcessableByteArray msg = new CMSProcessableByteArray(contentBytes);
                    CMSSignedData signedData = gen.generate(msg, false);
                    return signedData.getEncoded();
                } catch (Exception ex) {
                    throw new IOException("Khong the sinh chu ky so CMS", ex);
                }
            };

            document.addSignature(pdSignature, signatureInterface);
            document.saveIncremental(baos);

            log.info("Ky so PKI X.509 va khoa niem phong PDF thanh cong.");
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Loi khi ky so dien tu PKI tren PDF", e);
            throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR);
        }
    }

    @Override
    public byte[] createSamplePdf(String title, String content) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText(toAscii(title));
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(50, 700);
                cs.showText(toAscii(content));
                cs.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void setCertificationPermissions(PDDocument doc, PDSignature sig, int accessPermissions) {
        COSDictionary sigDict = sig.getCOSObject();
        COSDictionary transformParams = new COSDictionary();
        transformParams.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        transformParams.setInt(COSName.P, accessPermissions);
        transformParams.setName(COSName.V, "1.2");

        COSDictionary sigRef = new COSDictionary();
        sigRef.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        sigRef.setItem(COSName.getPDFName("TransformMethod"), COSName.getPDFName("DocMDP"));
        sigRef.setItem(COSName.getPDFName("TransformParams"), transformParams);

        COSArray referenceArray = new COSArray();
        referenceArray.add(sigRef);
        sigDict.setItem(COSName.getPDFName("Reference"), referenceArray);

        COSDictionary perms = new COSDictionary();
        perms.setItem(COSName.getPDFName("DocMDP"), sigDict);
        doc.getDocumentCatalog().getCOSObject().setItem(COSName.PERMS, perms);
    }

    private String toAscii(String input) {
        if (input == null) return "";
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("")
                .replace("đ", "d")
                .replace("Đ", "D");
    }
}
