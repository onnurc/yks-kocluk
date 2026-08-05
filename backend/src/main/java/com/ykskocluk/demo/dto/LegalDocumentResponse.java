package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.LegalDocumentType;
import java.time.Instant;

public record LegalDocumentResponse(Long id, LegalDocumentType type, String version,
                                    String title, String content, String contentHash,
                                    Instant effectiveAt) { }
