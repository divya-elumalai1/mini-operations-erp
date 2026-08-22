package com.minierp.repository;

import com.minierp.domain.InternalTransfer;
import com.minierp.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternalTransferRepository extends JpaRepository<InternalTransfer, Long> {

    @Query(value = "SELECT nextval('trf_number_seq')", nativeQuery = true)
    long nextTransferNumber();

    @Query(value = """
            SELECT t FROM InternalTransfer t
            JOIN FETCH t.item it
            JOIN FETCH t.sourceLocation s
            JOIN FETCH t.destinationLocation d
            JOIN FETCH t.createdBy cb
            LEFT JOIN FETCH t.dispatchedBy
            LEFT JOIN FETCH t.receivedBy
            WHERE (:status IS NULL OR t.status = :status)
            """,
            countQuery = """
                    SELECT COUNT(t) FROM InternalTransfer t
                    WHERE (:status IS NULL OR t.status = :status)
                    """)
    Page<InternalTransfer> search(@Param("status") TransferStatus status, Pageable pageable);

    @Query(value = """
            SELECT t FROM InternalTransfer t
            JOIN FETCH t.item
            JOIN FETCH t.sourceLocation
            JOIN FETCH t.destinationLocation
            JOIN FETCH t.createdBy
            LEFT JOIN FETCH t.dispatchedBy
            LEFT JOIN FETCH t.receivedBy
            WHERE t.id = :id
            """)
    java.util.Optional<InternalTransfer> findWithDetailsById(@Param("id") Long id);

    /**
     * Status-guarded transition REQUESTED -> DISPATCHED. Returns 0 when another
     * transaction already dispatched this transfer (or it was never requested),
     * making concurrent double-dispatch impossible.
     */
    @Modifying
    @Query(value = """
            UPDATE internal_transfers
               SET status = 'DISPATCHED', dispatched_by = :userId,
                   dispatched_at = now(), updated_at = now()
             WHERE id = :id AND status = 'REQUESTED'
            """, nativeQuery = true)
    int markDispatched(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Status-guarded transition DISPATCHED -> RECEIVED. Returns 0 when the transfer
     * was already received (duplicate receipt) or is not in DISPATCHED state —
     * this is what makes receiving a transfer twice impossible.
     */
    @Modifying
    @Query(value = """
            UPDATE internal_transfers
               SET status = 'RECEIVED', received_by = :userId,
                   received_at = now(), updated_at = now()
             WHERE id = :id AND status = 'DISPATCHED'
            """, nativeQuery = true)
    int markReceived(@Param("id") Long id, @Param("userId") Long userId);
}
