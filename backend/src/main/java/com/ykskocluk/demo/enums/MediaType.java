package com.ykskocluk.demo.enums;

public enum MediaType {
    PROFILE_IMAGE,
    /** Historical R2 type. New coach intro videos are admin-managed YouTube references. */
    @Deprecated(forRemoval = false)
    COACH_INTRO_VIDEO,
    /** Reserved intentionally for future authenticated, PRIVATE legal/user documents. */
    DOCUMENT
}
