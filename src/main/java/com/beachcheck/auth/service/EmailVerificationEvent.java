package com.beachcheck.auth.service;

public record EmailVerificationEvent(String email, String verificationLink) {}
