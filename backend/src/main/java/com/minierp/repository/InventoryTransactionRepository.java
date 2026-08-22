package com.minierp.repository;

import com.minierp.domain.InventoryTransaction;
import com.minierp.domain.InventoryTxType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query(value = """
            SELECT t FROM InventoryTransaction t
            JOIN FETCH t.item it
            JOIN FETCH t.location l
            JOIN FETCH t.performedBy u
            LEFT JOIN FETCH t.batch b
            WHERE (:itemId IS NULL OR it.id = :itemId)
              AND (:locationId IS NULL OR l.id = :locationId)
              AND (:type IS NULL OR t.txType = :type)
            """,
            countQuery = """
                    SELECT COUNT(t) FROM InventoryTransaction t
                    WHERE (:itemId IS NULL OR t.item.id = :itemId)
                      AND (:locationId IS NULL OR t.location.id = :locationId)
                      AND (:type IS NULL OR t.txType = :type)
                    """)
    Page<InventoryTransaction> search(@Param("itemId") Long itemId,
                                      @Param("locationId") Long locationId,
                                      @Param("type") InventoryTxType type,
                                      Pageable pageable);
}
