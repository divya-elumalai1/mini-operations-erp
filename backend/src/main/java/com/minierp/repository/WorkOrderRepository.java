package com.minierp.repository;

import com.minierp.domain.WorkOrder;
import com.minierp.domain.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    @Query(value = "SELECT nextval('wo_number_seq')", nativeQuery = true)
    long nextWorkOrderNumber();

    @Query(value = """
            SELECT w FROM WorkOrder w
            JOIN FETCH w.item it
            JOIN FETCH w.location l
            JOIN FETCH w.assignedUser au
            JOIN FETCH w.createdBy cb
            WHERE (:status IS NULL OR w.status = :status)
              AND (:locationId IS NULL OR l.id = :locationId)
            """,
            countQuery = """
                    SELECT COUNT(w) FROM WorkOrder w
                    WHERE (:status IS NULL OR w.status = :status)
                      AND (:locationId IS NULL OR w.location.id = :locationId)
                    """)
    Page<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("locationId") Long locationId,
                           Pageable pageable);

    @Query(value = """
            SELECT w FROM WorkOrder w
            JOIN FETCH w.item
            JOIN FETCH w.location
            JOIN FETCH w.assignedUser
            JOIN FETCH w.createdBy
            WHERE w.id = :id
            """)
    java.util.Optional<WorkOrder> findWithDetailsById(@Param("id") Long id);
}
