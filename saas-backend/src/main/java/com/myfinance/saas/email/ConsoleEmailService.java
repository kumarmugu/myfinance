package com.myfinance.saas.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Console email provider: renders the branded template and logs the subject + plain-text body
 * (address masked). Useful for local development and demos without an SMTP server.
 * Active when {@code app.email.provider=console}.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "console")
@Slf4j
public class ConsoleEmailService extends AbstractTemplatedEmailService {

    public ConsoleEmailService(EmailTemplates templates) {
        super(templates);
    }

    @Override
    protected void deliver(String toEmail, EmailContent content) {
        log.info("[email:console] to={} subject=\"{}\"\n{}",
                EmailMasking.mask(toEmail), content.subject(), content.text());
    }
}
