package com.ykskocluk.demo.dto;

public record AdminUserDetailResponse(
        AdminUserDirectoryResponse user,
        String profileImageUrl,
        Long profileImageAssetId
) { }
