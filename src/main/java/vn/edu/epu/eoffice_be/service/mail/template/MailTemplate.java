package vn.edu.epu.eoffice_be.service.mail.template;

public interface MailTemplate<T> {

	String subject(T context);

	String htmlBody(T context);
}
