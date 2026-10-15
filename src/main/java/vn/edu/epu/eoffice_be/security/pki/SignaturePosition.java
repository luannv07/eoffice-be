package vn.edu.epu.eoffice_be.security.pki;

/**
 * Tọa độ và kích thước vùng hiển thị chữ ký số trên trang PDF (Visual Signature Position).
 *
 * @param page   Số trang (1-based, ví dụ: 1 là trang đầu tiên)
 * @param x      Tọa độ X góc dưới bên trái (points, chuẩn PDF)
 * @param y      Tọa độ Y góc dưới bên trái (points, chuẩn PDF)
 * @param width  Chiều rộng khung chữ ký (points)
 * @param height Chiều cao khung chữ ký (points)
 */
public record SignaturePosition(
        int page,
        float x,
        float y,
        float width,
        float height
) {
    public SignaturePosition {
        if (page < 1) {
            page = 1;
        }
        if (width <= 0) {
            width = 150f;
        }
        if (height <= 0) {
            height = 50f;
        }
    }
}
