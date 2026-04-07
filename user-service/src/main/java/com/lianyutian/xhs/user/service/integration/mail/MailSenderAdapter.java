package com.lianyutian.xhs.user.service.integration.mail;

public interface MailSenderAdapter {

    void send(String to, String subject, String content);
}
