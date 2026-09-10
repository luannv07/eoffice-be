package vn.edu.epu.eoffice_be.service.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpMailSenderTest {

	@Mock
	JavaMailSender javaMailSender;

	@Test
	void sendShouldCreateHtmlMessageWithConfiguredSender() throws Exception {
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(javaMailSender.createMimeMessage()).thenReturn(message);
		SmtpMailSender smtpMailSender = sender();

		smtpMailSender.send("bob@example.com", "Reset password", "<p>Reset</p>");

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(javaMailSender).send(captor.capture());
		MimeMessage sent = captor.getValue();
		assertEquals("no-reply@eoffice.local", sent.getFrom()[0].toString());
		assertEquals("bob@example.com", sent.getAllRecipients()[0].toString());
		assertEquals("Reset password", sent.getSubject());
	}

	@Test
	void sendShouldMapMailFailuresToBusinessException() {
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(javaMailSender.createMimeMessage()).thenReturn(message);
		doThrow(new MailSendException("smtp-down")).when(javaMailSender).send(message);
		SmtpMailSender smtpMailSender = sender();

		BusinessException ex = assertThrows(BusinessException.class,
						() -> smtpMailSender.send("bob@example.com", "Reset password", "<p>Reset</p>"));

		assertEquals(ErrorCode.MAIL_SEND_FAILED, ex.getErrorCode());
	}

	private SmtpMailSender sender() {
		SmtpMailSender smtpMailSender = new SmtpMailSender(javaMailSender);
		ReflectionTestUtils.setField(smtpMailSender, "from", "no-reply@eoffice.local");
		return smtpMailSender;
	}
}
