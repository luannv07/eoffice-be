package vn.edu.epu.eoffice_be.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class SmtpMailSender implements MailSender {

	final JavaMailSender javaMailSender;

	@Value("${eoffice.mail.from:no-reply@eoffice.local}")
	String from;

	@Override
	@Async
	public void send(String to, String subject, String htmlBody) {
		try {
			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
			javaMailSender.send(message);
			log.info("[MAIL_SENT] Successfully sent email to={}, subject={}", to, subject);
		} catch (MessagingException | MailException ex) {
			log.error("[MAIL_SEND_FAILED] Failed to send email to={}, subject={}", to, subject, ex);
			throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
		}
	}
}
