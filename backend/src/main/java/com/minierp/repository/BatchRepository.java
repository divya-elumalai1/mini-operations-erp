package com.minierp.repository;

import com.minierp.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    Optional<Batch> findByItemIdAndBatchCode(Long itemId, String batchCode);
}
