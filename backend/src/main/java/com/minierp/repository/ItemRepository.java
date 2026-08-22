package com.minierp.repository;

import com.minierp.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    @Query("""
            SELECT i FROM Item i
            JOIN FETCH i.category
            ORDER BY i.sku
            """)
    List<Item> findAllWithCategory();

    @Query("""
            SELECT i FROM Item i
            JOIN FETCH i.category
            WHERE i.id = :id
            """)
    Optional<Item> findWithCategoryById(Long id);
}
