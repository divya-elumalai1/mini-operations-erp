package com.minierp.mapper;

import com.minierp.domain.Category;
import com.minierp.domain.Item;
import com.minierp.domain.Location;
import com.minierp.dto.CategoryResponse;
import com.minierp.dto.ItemResponse;
import com.minierp.dto.LocationResponse;

public final class MasterDataMapper {

    private MasterDataMapper() {
    }

    public static ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(), item.getSku(), item.getName(),
                item.getCategory().getId(), item.getCategory().getName(),
                item.getUnitOfMeasure(), item.isActive());
    }

    public static LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getCode(),
                location.getName(), location.isActive());
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
