package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

public record PackagePriceTierResponse(int monthsRemaining, BigDecimal price) {}
