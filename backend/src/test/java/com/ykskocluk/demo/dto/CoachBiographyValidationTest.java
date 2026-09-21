package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Track;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoachBiographyValidationTest {

    private static AutoCloseable validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validatorFactory = factory;
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() throws Exception {
        validatorFactory.close();
    }

    @Test
    void acceptsEmptyAndThousandCharacterBiographies() {
        assertThat(validator.validate(educationRequest(""))).isEmpty();
        assertThat(validator.validate(educationRequest("a".repeat(1000)))).isEmpty();
    }

    @Test
    void rejectsBiographyLongerThanOneThousandCharactersAcrossProfileInputs() {
        String tooLong = "a".repeat(1001);

        assertThat(validator.validate(educationRequest(tooLong)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("bio"));
        assertThat(validator.validate(new CoachProfileUpdateRequest(
                "Başlık", tooLong, 1L, null, null, null, Set.of(Track.NUMERICAL))))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("bio"));
        assertThat(validator.validate(new CoachProfileCreateRequest(
                "Başlık", tooLong, 1L, null, null, Set.of(Track.NUMERICAL))))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("bio"));
    }

    private CoachEducationUpdateRequest educationRequest(String bio) {
        return new CoachEducationUpdateRequest("Boğaziçi Üniversitesi", null, null, bio);
    }
}
