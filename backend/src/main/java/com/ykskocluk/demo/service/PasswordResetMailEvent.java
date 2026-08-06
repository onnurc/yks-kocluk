package com.ykskocluk.demo.service;

public record PasswordResetMailEvent(String email, String resetLink) {}
