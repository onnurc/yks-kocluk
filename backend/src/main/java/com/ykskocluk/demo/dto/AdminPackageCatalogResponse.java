package com.ykskocluk.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminPackageCatalogResponse(
        Integer yksExamYear,
        LocalDate yksExamDate,
        boolean yksExamActive,
        Integer applicableMonthsRemaining,
        List<AdminPackageResponse> packages
) {}
