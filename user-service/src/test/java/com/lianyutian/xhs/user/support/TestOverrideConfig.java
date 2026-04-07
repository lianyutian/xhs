package com.lianyutian.xhs.user.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

@TestConfiguration
public class TestOverrideConfig {

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                return new MimeMessage((Session) null);
            }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) {
                return new MimeMessage((Session) null);
            }

            @Override
            public void send(MimeMessage mimeMessage) {
            }

            @Override
            public void send(MimeMessage... mimeMessages) {
            }

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) {
            }

            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) {
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) {
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
            }
        };
    }

}
