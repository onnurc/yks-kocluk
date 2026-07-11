package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PackageResponse;
import com.ykskocluk.demo.mapper.PackageMapper;
import com.ykskocluk.demo.repository.PackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final PackageMapper packageMapper;

    public PackageService(PackageRepository packageRepository, PackageMapper packageMapper) {
        this.packageRepository = packageRepository;
        this.packageMapper = packageMapper;
    }

    @Transactional(readOnly = true)
    public List<PackageResponse> listActive() {
        return packageRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(packageMapper::toResponse)
                .toList();
    }
}
