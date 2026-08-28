package com.myfinance.saas.email;

import com.myfinance.saas.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * SMTP email provider using Spring's JavaMailSender. Sends multipart (HTML + plain text).
 * Active when {@code app.email.provider=smtp}. SMTP credentials come from configuration
 * (spring.mail.*), sourced from the environment/secret store.
 *
 * Delivery failures are logged but not rethrown, so a transient email outage does not break
 * signup/payment flows (those flows are designed to tolerate email failure).
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
@Slf4j
public class SmtpEmailService extends AbstractTemplatedEmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public SmtpEmailService(EmailTemplates templates, JavaMailSender mailSender, AppProperties appProperties) {
        super(templates);
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Override
    protected void deliver(String toEmail, EmailContent content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.getEmail().getFrom(), appProperties.getEmail().getFromName());
            helper.setTo(toEmail);
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            mailSender.send(message);
            log.info("[email:smtp] sent \"{}\" to {}", content.subject(), EmailMasking.mask(toEmail));
        } catch (Exception e) {
            // Non-fatal: log and continue. Reconciliation / retries handle re-delivery where needed.
            log.error("[email:smtp] failed to send \"{}\" to {}: {}",
                    content.subject(), EmailMasking.mask(toEmail), e.getMessage());
        }
    }
}
