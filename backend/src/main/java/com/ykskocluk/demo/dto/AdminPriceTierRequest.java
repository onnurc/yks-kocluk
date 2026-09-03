package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminPriceTierRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal price
) {}
