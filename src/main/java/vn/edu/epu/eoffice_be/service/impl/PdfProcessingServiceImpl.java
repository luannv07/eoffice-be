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
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.service.PdfProcessingService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfProcessingServiceImpl implements PdfProcessingService {

    private final PrivateKey epuPrivateKey;
    private final X509Certificate epuCertificate;

    public PdfProcessingServiceImpl() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.epuPrivateKey = keyPair.getPrivate();

            Instant now = Instant.now();
            Date startDate = Date.from(now.minus(Duration.ofHours(1)));
            Date endDate = Date.from(now.plus(Duration.ofDays(365 * 5)));
            BigInteger serialNumber = BigInteger.valueOf(now.toEpochMilli());
            X500Name dnName = new X500Name("CN=Truong Dai hoc Dien Luc - EPU, OU=Ban Giam Hieu, O=EPU, C=VN");

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName, serialNumber, startDate, endDate, dnName, keyPair.getPublic());
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(this.epuPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            this.epuCertificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(certHolder);
            log.info("Khoi tao chung thu so EPU PKI X.509 thanh cong.");
        } catch (Exception e) {
            log.error("Loi khi khoi tao chung thu so PKI", e);
            throw new IllegalStateException("Khong the khoi tao dich vu PKI", e);
        }
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
