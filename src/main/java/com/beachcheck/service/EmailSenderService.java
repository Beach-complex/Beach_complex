package com.beachcheck.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

  private final JavaMailSender mailSender;

  public EmailSenderService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void send(String from, String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    if (from != null && !from.isBlank()) {
      message.setFrom(from);
    }
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }
}
