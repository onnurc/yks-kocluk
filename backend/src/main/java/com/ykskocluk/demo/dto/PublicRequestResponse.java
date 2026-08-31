package com.ykskocluk.demo.dto;

public record PublicRequestResponse(String message) {
    public static final String REGISTRATION_MESSAGE =
            "E-posta adresinizi kontrol edin. Kayıt işleminiz uygunsa doğrulama kodu gönderilecektir. "
                    + "Bu e-posta adresiyle daha önce hesap oluşturduysanız mevcut hesabınızla giriş yapabilirsiniz.";
    public static final String COACH_APPLICATION_MESSAGE =
            "Başvuru talebiniz alındı. Başvurunuzun durumuyla ilgili gerekli bilgilendirme "
                    + "e-posta adresiniz üzerinden yapılacaktır.";

    public static PublicRequestResponse registrationAccepted() {
        return new PublicRequestResponse(REGISTRATION_MESSAGE);
    }

    public static PublicRequestResponse coachApplicationAccepted() {
        return new PublicRequestResponse(COACH_APPLICATION_MESSAGE);
    }
}
