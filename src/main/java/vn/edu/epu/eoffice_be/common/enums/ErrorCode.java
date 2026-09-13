package vn.edu.epu.eoffice_be.common.enums;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_REQUEST("BAD_REQUEST", "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Không có quyền truy cập", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "Không tìm thấy dữ liệu", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR("VALIDATION_ERROR", "Lỗi validate dữ liệu", HttpStatus.BAD_REQUEST),
    BUSINESS_ERROR("BUSINESS_ERROR", "Lỗi nghiệp vụ", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Tài khoản đã bị khóa", HttpStatus.LOCKED),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN),
    RATE_LIMITED("RATE_LIMITED", "Quá nhiều lần thử đăng nhập, vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_TOKEN("INVALID_TOKEN", "Token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED),
    PROFILE_INCOMPLETE("PROFILE_INCOMPLETE", "Bạn cần hoàn thiện profile trước", HttpStatus.FORBIDDEN),
    PASSWORD_EXPIRED("PASSWORD_EXPIRED", "Mật khẩu đã hết hạn, vui lòng đổi mật khẩu", HttpStatus.UNAUTHORIZED),
    PASSWORD_RESET_TOKEN_INVALID("PASSWORD_RESET_TOKEN_INVALID", "Link đặt lại mật khẩu không hợp lệ", HttpStatus.BAD_REQUEST),
    PASSWORD_RESET_TOKEN_EXPIRED("PASSWORD_RESET_TOKEN_EXPIRED", "Link đặt lại mật khẩu đã hết hạn", HttpStatus.BAD_REQUEST),
    MAIL_SEND_FAILED("MAIL_SEND_FAILED", "Không gửi được email", HttpStatus.INTERNAL_SERVER_ERROR),
    USERNAME_ALREADY_EXISTS("USERNAME_ALREADY_EXISTS", "Tên đăng nhập đã tồn tại", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email đã được sử dụng", HttpStatus.CONFLICT),
    INVALID_ENUM_VALUE("INVALID_ENUM_VALUE", "Giá trị không hợp lệ", HttpStatus.BAD_REQUEST),
    ORG_NODE_CODE_EXISTS("ORG_NODE_CODE_EXISTS", "Mã đơn vị đã tồn tại", HttpStatus.CONFLICT),
    ORG_NODE_NOT_FOUND("ORG_NODE_NOT_FOUND", "Không tìm thấy đơn vị", HttpStatus.NOT_FOUND),
    ORG_NODE_PARENT_NOT_FOUND("ORG_NODE_PARENT_NOT_FOUND", "Đơn vị cha không tồn tại", HttpStatus.BAD_REQUEST),
    ORG_NODE_HAS_CHILDREN("ORG_NODE_HAS_CHILDREN", "Đơn vị còn đơn vị con, không thể xóa", HttpStatus.BAD_REQUEST),
    ORG_NODE_CYCLE("ORG_NODE_CYCLE", "Không thể chuyển đơn vị vào chính nó hoặc đơn vị con", HttpStatus.BAD_REQUEST),
    ORG_NODE_TYPE_INVALID("ORG_NODE_TYPE_INVALID", "Loại đơn vị không hợp lệ", HttpStatus.BAD_REQUEST),
    DICTIONARY_ITEM_ALREADY_EXISTS("DICTIONARY_ITEM_ALREADY_EXISTS", "Mục từ điển đã tồn tại trong nhóm", HttpStatus.CONFLICT),
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "Không tìm thấy văn bản", HttpStatus.NOT_FOUND),
    INVALID_DOCUMENT_STATUS("INVALID_DOCUMENT_STATUS", "Trạng thái văn bản không hợp lệ", HttpStatus.BAD_REQUEST),
    /**
     * @deprecated Giữ lại để đảm bảo tương thích ngược 100% với hợp đồng API công khai hiện có của workflow và document.
     */
    @Deprecated
    DOCUMENT_INVALID_STATUS("DOCUMENT_INVALID_STATUS", "Trạng thái văn bản không hợp lệ cho thao tác này", HttpStatus.BAD_REQUEST),
    WORKFLOW_TRANSITION_NOT_ALLOWED("WORKFLOW_TRANSITION_NOT_ALLOWED", "Hành động luồng duyệt không được phép với trạng thái hiện tại", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_WORKFLOW_ACTION("UNAUTHORIZED_WORKFLOW_ACTION", "Bạn không có quyền thực hiện hành động này trên văn bản", HttpStatus.FORBIDDEN),
    SIGNATURE_PROCESS_ERROR("SIGNATURE_PROCESS_ERROR", "Lỗi trong quá trình xử lý ký số hoặc ký nháy PDF", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_ERROR("STORAGE_ERROR", "Lỗi trong quá trình lưu trữ tệp tin", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCUMENT_FILE_MISSING("DOCUMENT_FILE_MISSING", "Văn bản chưa có tệp đính kèm để ký", HttpStatus.BAD_REQUEST),
    DOCUMENT_PRIMARY_FILE_MISSING("DOCUMENT_PRIMARY_FILE_MISSING", "Văn bản chưa có tệp đính kèm chính để ký", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_READY_FOR_PUBLISH("DOCUMENT_NOT_READY_FOR_PUBLISH", "Văn bản chưa đủ điều kiện để ký phát hành", HttpStatus.BAD_REQUEST),
    INVALID_WORKFLOW_STATE("INVALID_WORKFLOW_STATE", "Trạng thái văn bản không hợp lệ trong luồng duyệt", HttpStatus.BAD_REQUEST),
    DOCUMENT_INCOMPLETE_DOSSIER("DOCUMENT_INCOMPLETE_DOSSIER", "Hồ sơ văn bản chưa đầy đủ (cần có tệp đính kèm chính hoặc nội dung)", HttpStatus.BAD_REQUEST),
    DOCUMENT_CANNOT_RECALL("DOCUMENT_CANNOT_RECALL", "Không thể thu hồi văn bản do người duyệt đã xử lý", HttpStatus.BAD_REQUEST),
    DOCUMENT_VERSION_NOT_FOUND("DOCUMENT_VERSION_NOT_FOUND", "Phiên bản văn bản không tồn tại", HttpStatus.NOT_FOUND),
    DOCUMENT_ATTACHMENT_NOT_FOUND("DOCUMENT_ATTACHMENT_NOT_FOUND", "Tệp đính kèm không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
    FILE_INVALID_FORMAT("FILE_INVALID_FORMAT", "Định dạng tệp không được hỗ trợ. Chỉ chấp nhận tệp DOCX hoặc PDF", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("FILE_SIZE_EXCEEDED", "Dung lượng tệp vượt quá giới hạn cho phép", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_UNSAFE("FILE_UNSAFE", "Tệp tin không an toàn hoặc chứa nội dung độc hại", HttpStatus.BAD_REQUEST),
    FILE_STORAGE_ERROR("FILE_STORAGE_ERROR", "Lỗi trong quá trình lưu trữ tệp tin", HttpStatus.INTERNAL_SERVER_ERROR),
    APPROVER_NOT_ELIGIBLE("APPROVER_NOT_ELIGIBLE", "Người được chọn không có thẩm quyền duyệt văn bản này", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}