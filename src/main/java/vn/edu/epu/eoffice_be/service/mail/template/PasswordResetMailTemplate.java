package vn.edu.epu.eoffice_be.service.mail.template;

import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailTemplate implements MailTemplate<PasswordResetMailTemplate.Context> {

    public record Context(String resetLink) {
    }

    @Override
    public String subject(Context context) {
        return "Đặt lại mật khẩu eOffice";
    }

    @Override
    public String htmlBody(Context context) {
        return """
                <p>Yêu cầu đặt lại mật khẩu eOffice đã được tạo.</p>
                <p>Vui lòng bấm vào liên kết dưới đây để đặt mật khẩu mới:</p>
                <p><a href=\"%s\">Đặt lại mật khẩu</a></p>
                <p>Liên kết sẽ hết hạn sau thời gian cấu hình. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                """.formatted(context.resetLink());
    }
}
