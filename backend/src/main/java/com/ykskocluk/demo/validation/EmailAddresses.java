package com.ykskocluk.demo.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailAddresses {
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern TLD = Pattern.compile("[A-Za-z]{2,63}");

    private EmailAddresses() { }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String rawEmail) {
        String email = rawEmail == null ? null : rawEmail.trim();
        if (email == null || email.isEmpty() || email.length() > 254) return false;
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at > 64 || at == email.length() - 1) return false;

        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (!LOCAL.matcher(local).matches() || local.startsWith(".") || local.endsWith(".")
                || local.contains("..") || domain.startsWith(".") || domain.endsWith(".")
                || domain.contains("..")) return false;

        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2 || !TLD.matcher(labels[labels.length - 1]).matches()) return false;
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) return false;
        }
        return true;
    }
}
