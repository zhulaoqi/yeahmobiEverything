package com.yeahmobi.everything.opsos.notify;

import com.yeahmobi.everything.common.Config;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Email channel for notification hub.
 */
public class EmailNotifyChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifyChannel.class);
    private final Config config;

    public EmailNotifyChannel(Config config) {
        this.config = config;
    }

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.EMAIL;
    }

    @Override
    public NotifyResult send(String target, NotifyMessage message) {
        String to = target == null ? "" : target.trim();
        if (to.isBlank()) {
            return new NotifyResult(type(), false, "收件邮箱为空");
        }
        String host = config.get("smtp.host", "smtp.qq.com");
        int port = config.getInt("smtp.port", 465);
        String username = config.get("smtp.username", "");
        String password = config.get("smtp.password", "");
        String fromAddress = config.get("smtp.from", username);
        boolean useSsl = "true".equalsIgnoreCase(config.get("smtp.ssl", "true"));
        if (username.isBlank() || password.isBlank()) {
            log.info("Email channel simulate send: to={}, title={}", to,
                    (message == null ? "" : message.title()));
            return new NotifyResult(type(), true, "SMTP 未配置，已模拟发送");
        }
        try {
            Session mailSession = createSession(host, port, username, password, useSsl);
            MimeMessage msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(fromAddress, "Everything Assistant", "UTF-8"));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject(message == null ? "提醒通知" : safe(message.title()), "UTF-8");
            String html = message != null && message.detailHtml() != null && !message.detailHtml().isBlank()
                    ? message.detailHtml()
                    : "<html><body><pre>" + safe(message == null ? "" : message.detailText()) + "</pre></body></html>";
            msg.setContent(html, "text/html; charset=UTF-8");
            Transport.send(msg);
            return new NotifyResult(type(), true, "发送成功");
        } catch (Exception e) {
            log.warn("Email notify failed", e);
            return new NotifyResult(type(), false, e.getMessage() == null ? "发送失败" : e.getMessage());
        }
    }

    private Session createSession(String host, int port, String username, String password, boolean useSsl) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(username, password);
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
