package com.minierp.repository;

import com.minierp.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
