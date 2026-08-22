package com.minierp.repository;

import com.minierp.domain.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByItemIdAndLocationId(Long itemId, Long locationId);

    @Query(value = """
            SELECT i FROM Inventory i
            JOIN FETCH i.item it
            JOIN FETCH i.location l
            WHERE (:itemId IS NULL OR i.item.id = :itemId)
              AND (:locationId IS NULL OR i.location.id = :locationId)
            """,
            countQuery = """
                    SELECT COUNT(i) FROM Inventory i
                    WHERE (:itemId IS NULL OR i.item.id = :itemId)
                      AND (:locationId IS NULL OR i.location.id = :locationId)
                    """)
    Page<Inventory> search(@Param("itemId") Long itemId,
                           @Param("locationId") Long locationId,
                           Pageable pageable);

    /**
     * Atomically increases reserved quantity only if enough unreserved stock exists.
     * Returns 0 when the row is missing or available stock is insufficient — the
     * row lock taken by this UPDATE serializes concurrent reservers and the predicate
     * is re-evaluated against the latest committed version, so overselling is impossible.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET reserved_qty = reserved_qty + :qty,
                   updated_at = now()
             WHERE item_id = :itemId AND location_id = :locationId
               AND physical_qty - reserved_qty >= :qty
            """, nativeQuery = true)
    int reserve(@Param("itemId") Long itemId,
                @Param("locationId") Long locationId,
                @Param("qty") BigDecimal qty);

    /** Atomically releases reservation; returns 0 if nothing (or not enough) is reserved. */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET reserved_qty = reserved_qty - :qty,
                   updated_at = now()
             WHERE item_id = :itemId AND location_id = :locationId
               AND reserved_qty >= :qty
            """, nativeQuery = true)
    int releaseReservation(@Param("itemId") Long itemId,
                           @Param("locationId") Long locationId,
                           @Param("qty") BigDecimal qty);

    /**
     * Ships unreserved stock out of a location (transfer dispatch). Guarded by the
     * available-quantity predicate so dispatching more than available fails atomically.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET physical_qty = physical_qty - :qty,
                   updated_at = now()
             WHERE item_id = :itemId AND location_id = :locationId
               AND physical_qty - reserved_qty >= :qty
            """, nativeQuery = true)
    int consumeAvailableStock(@Param("itemId") Long itemId,
                              @Param("locationId") Long locationId,
                              @Param("qty") BigDecimal qty);

    /**
     * Adds physical stock, creating the inventory row when missing. The upsert makes
     * concurrent receipts/receipts-at-new-locations atomic without read-modify-write.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory (item_id, location_id, physical_qty, reserved_qty)
            VALUES (:itemId, :locationId, :qty, 0)
            ON CONFLICT (item_id, location_id)
            DO UPDATE SET physical_qty = inventory.physical_qty + EXCLUDED.physical_qty,
                          updated_at = now()
            """, nativeQuery = true)
    int addPhysicalStockUpsert(@Param("itemId") Long itemId,
                               @Param("locationId") Long locationId,
                               @Param("qty") BigDecimal qty);
}
