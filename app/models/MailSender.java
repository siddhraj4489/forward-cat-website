package models;

import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Play;

import javax.annotation.concurrent.ThreadSafe;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

@ThreadSafe
public class MailSender {

    private static final String TEXT_HTML = "text/html; charset=UTF-8";
    private static final String MAIL_SERVER_HOST = "mailServerHost";
    private static final String MAIL_SENDER_ADDRESS = "mailSenderAddress";

    private final Properties mailServerProperties = System.getProperties();
    private final InternetAddress senderAddress;

    public MailSender() throws AddressException {
        String mailServerHost = Play.application().configuration().getString(MAIL_SERVER_HOST);
        mailServerProperties.setProperty("mail.smtp.host", mailServerHost);

        String senderAddressStr = Play.application().configuration().getString(MAIL_SENDER_ADDRESS);
        this.senderAddress = new InternetAddress(senderAddressStr);
    }

    public void sendHtmlMail(MailAddress to, String subject, String content) {
        Session session = Session.getDefaultInstance(mailServerProperties);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(senderAddress);
            message.addRecipient(Message.RecipientType.TO,
                    new InternetAddress(to.toString()));
            message.setSubject(subject);
            message.setContent(content, TEXT_HTML);

            Transport.send(message);
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
        }
    }

    private static Logger logger = LoggerFactory.getLogger(MailSender.class.getName());
}