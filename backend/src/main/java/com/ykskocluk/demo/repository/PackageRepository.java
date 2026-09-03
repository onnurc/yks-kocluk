package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.enums.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<Package, Long> {

    List<Package> findByActiveTrueOrderByPriceAsc();

    List<Package> findByPackageTypeIsNotNullOrderByIdAsc();

    Optional<Package> findByPackageType(PackageType packageType);
}
