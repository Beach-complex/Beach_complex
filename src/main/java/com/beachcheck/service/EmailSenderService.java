package com.beachcheck.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class EmailSenderService {

  private final JavaMailSender mailSender;
  private final String defaultFrom;

  public EmailSenderService(
      JavaMailSender mailSender, @Value("${app.mail.default-from}") String defaultFrom) {
    this.mailSender = mailSender;
    Assert.hasText(defaultFrom, "app.mail.default-from must be configured");
    this.defaultFrom = defaultFrom;
  }

  public void send(String from, String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    String resolvedFrom = (from == null || from.isBlank()) ? defaultFrom : from;
    message.setFrom(resolvedFrom);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }
}
