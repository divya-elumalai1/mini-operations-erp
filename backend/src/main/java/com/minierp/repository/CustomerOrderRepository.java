package com.minierp.repository;

import com.minierp.domain.CustomerOrder;
import com.minierp.domain.CustomerOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @Query(value = "SELECT nextval('so_number_seq')", nativeQuery = true)
    long nextOrderNumber();

    @Query(value = """
            SELECT o FROM CustomerOrder o
            JOIN FETCH o.createdBy
            WHERE (:status IS NULL OR o.status = :status)
            """,
            countQuery = """
                    SELECT COUNT(o) FROM CustomerOrder o
                    WHERE (:status IS NULL OR o.status = :status)
                    """)
    Page<CustomerOrder> search(@Param("status") CustomerOrderStatus status, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT o FROM CustomerOrder o
            JOIN FETCH o.createdBy
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.item it
            LEFT JOIN FETCH i.location l
            WHERE o.id = :id
            """)
    java.util.Optional<CustomerOrder> findWithDetailsById(@Param("id") Long id);
}
