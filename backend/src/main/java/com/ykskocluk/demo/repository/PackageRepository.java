package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Package;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageRepository extends JpaRepository<Package, Long> {

    List<Package> findByActiveTrueOrderByPriceAsc();
}
