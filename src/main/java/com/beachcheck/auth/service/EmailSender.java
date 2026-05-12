package com.beachcheck.auth.service;

public interface EmailSender {
  void send(String from, String to, String subject, String body);
}
