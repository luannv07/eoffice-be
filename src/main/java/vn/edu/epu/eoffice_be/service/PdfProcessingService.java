package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.dto.StampInfo;

public interface PdfProcessingService {

    /**
     * UC13: Nhúng con dấu Digital Stamp ký nháy vào trang cuối cùng của file PDF.
     *
     * @param pdfBytes  Dữ liệu PDF gốc
     * @param stampInfo Thông tin con dấu (người ký, chức vụ, ngày giờ)
     * @return Dữ liệu PDF mới đã được đóng dấu ký nháy
     */
    byte[] applyInitialSignStamp(byte[] pdfBytes, StampInfo stampInfo);

    /**
     * UC14: Ký số điện tử PKI X.509 PAdES và đặt cờ niêm phong CERTIFIED_NO_CHANGES_ALLOWED.
     *
     * @param pdfBytes   Dữ liệu PDF cần ký
     * @param signerName Tên người ký phát hành (ví dụ: Hieu Truong - Dai Hoc Dien Luc)
     * @param reason     Lý do ký (ví dụ: Ky ban hanh van ban dien tu)
     * @return Dữ liệu PDF chính thức đã được ký số và bảo vệ toàn vẹn
     */
    byte[] signAndCertifyPdf(byte[] pdfBytes, String signerName, String reason);

    /**
     * Hàm tiện ích tạo nhanh 1 file PDF mẫu (để phục vụ kiểm thử và chạy thử nghiệm)
     */
    byte[] createSamplePdf(String title, String content);
}
