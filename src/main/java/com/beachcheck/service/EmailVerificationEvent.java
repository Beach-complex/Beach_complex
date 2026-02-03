package com.beachcheck.service;

public record EmailVerificationEvent(String email, String verificationLink) {}
