package com.ykskocluk.demo.repository;

/** Projection: number of sessions of a given status per coach profile. */
public record CoachSessionCount(Long coachProfileId, Long count) {
}
