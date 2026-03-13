package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.common.Config;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SMTP implementation of {@link EmailService}.
 * <p>
 * Sends verification code emails via SMTP with SSL/TLS support.
 * Supports common mail providers: QQ Mail, 163, Gmail, Outlook, etc.
 * </p>
 *
 * <h3>Required configuration in application.properties:</h3>
 * <pre>
 * smtp.host=smtp.qq.com
 * smtp.port=465
 * smtp.username=your-email@qq.com
 * smtp.password=your-authorization-code
 * smtp.from=your-email@qq.com
 * smtp.ssl=true
 * </pre>
 */
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final boolean useSsl;

    /**
     * Creates an SmtpEmailService from application configuration.
     *
     * @param config the application configuration
     */
    public SmtpEmailService(Config config) {
        this.host = config.get("smtp.host", "smtp.qq.com");
        this.port = Integer.parseInt(config.get("smtp.port", "465"));
        this.username = config.get("smtp.username", "");
        this.password = config.get("smtp.password", "");
        this.fromAddress = config.get("smtp.from", this.username);
        this.useSsl = Boolean.parseBoolean(config.get("smtp.ssl", "true"));
    }

    /**
     * Creates an SmtpEmailService with explicit parameters (for testing).
     */
    SmtpEmailService(String host, int port, String username, String password,
                     String fromAddress, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.useSsl = useSsl;
    }

    @Override
    public boolean sendVerificationCode(String toEmail, String code) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Recipient email is null or blank");
            return false;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("SMTP credentials not configured. Falling back to console log.");
            log.info("【模拟发送】验证码已发送到 {}: {}", toEmail, code);
            return true;
        }

        try {
            jakarta.mail.Session mailSession = createMailSession();

            jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(mailSession);
            message.setFrom(new jakarta.mail.internet.InternetAddress(fromAddress, "Yeahmobi Everything", "UTF-8"));
            message.addRecipient(jakarta.mail.Message.RecipientType.TO,
                    new jakarta.mail.internet.InternetAddress(toEmail));
            message.setSubject("Yeahmobi Everything - 邮箱验证码", "UTF-8");
            message.setContent(buildHtmlContent(code), "text/html; charset=UTF-8");

            jakarta.mail.Transport.send(message);

            log.info("Verification code email sent to: {}", toEmail);
            return true;

        } catch (jakarta.mail.AuthenticationFailedException e) {
            log.warn("SMTP authentication failed. Check smtp.username and smtp.password.", e);
            return false;
        } catch (jakarta.mail.MessagingException e) {
            log.warn("Failed to send verification email to: {}", toEmail, e);
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error sending email to: {}", toEmail, e);
            return false;
        }
    }

    /**
     * Creates a Jakarta Mail Session with SMTP configuration.
     */
    private jakarta.mail.Session createMailSession() {
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

        return jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(username, password);
            }
        });
    }

    /**
     * Builds a professional HTML email template for the verification code.
     *
     * @param code the 6-digit verification code
     * @return HTML content string
     */
    static String buildHtmlContent(String code) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f6f8fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border:1px solid #d0d7de;border-radius:8px;overflow:hidden;">
                    <!-- Header -->
                    <tr><td style="background:#1f883d;padding:24px 32px;">
                        <h1 style="margin:0;color:#ffffff;font-size:20px;font-weight:600;">Yeahmobi Everything</h1>
                    </td></tr>
                    <!-- Body -->
                    <tr><td style="padding:32px;">
                        <p style="margin:0 0 16px;font-size:16px;color:#1f2328;">你好，</p>
                        <p style="margin:0 0 24px;font-size:14px;color:#656d76;line-height:1.6;">
                            你正在注册 Yeahmobi Everything 账号，以下是你的邮箱验证码：
                        </p>
                        <div style="text-align:center;margin:24px 0;">
                            <span style="display:inline-block;background:#f6f8fa;border:1px solid #d0d7de;border-radius:8px;padding:16px 40px;font-size:32px;font-weight:700;letter-spacing:8px;color:#1f883d;">
                                %s
                            </span>
                        </div>
                        <p style="margin:24px 0 0;font-size:13px;color:#656d76;line-height:1.6;">
                            验证码有效期为 <strong>5 分钟</strong>，请尽快使用。<br>
                            如果你没有请求此验证码，请忽略此邮件。
                        </p>
                    </td></tr>
                    <!-- Footer -->
                    <tr><td style="padding:16px 32px;border-top:1px solid #d0d7de;background:#f6f8fa;">
                        <p style="margin:0;font-size:12px;color:#8b949e;text-align:center;">
                            &copy; Yeahmobi Everything &middot; AI 驱动的智能工作助手
                        </p>
                    </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(code);
    }
}
