package com.beachcheck.service;

public interface EmailSender {
  void send(String from, String to, String subject, String body);
}