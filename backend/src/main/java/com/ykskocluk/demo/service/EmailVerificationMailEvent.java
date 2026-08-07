package com.ykskocluk.demo.service;

public record EmailVerificationMailEvent(String email, String code) {
}
