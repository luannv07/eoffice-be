package vn.edu.epu.eoffice_be.service.mail.template;

import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailTemplate implements MailTemplate<PasswordResetMailTemplate.Context> {

	@Override
	public String subject(Context context) {
		return "Đặt lại mật khẩu";
	}

	@Override
	public String htmlBody(Context context) {
		return """
						<p>Yêu cầu đặt lại mật khẩu đã được tạo.</p>
						<p>Vui lòng bấm vào liên kết dưới đây để đặt mật khẩu mới:</p>
						<p><a href=\"%s\">Đặt lại mật khẩu</a></p>
						<p>Liên kết sẽ hết hạn sau %s phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
						""".formatted(context.resetLink(), context.tokenTtlMinutes());
	}

	public record Context(String resetLink, int tokenTtlMinutes) {
	}
}
