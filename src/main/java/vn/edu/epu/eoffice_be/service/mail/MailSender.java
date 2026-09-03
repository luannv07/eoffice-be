package vn.edu.epu.eoffice_be.service.mail;

public interface MailSender {

    void send(String to, String subject, String htmlBody);
}
