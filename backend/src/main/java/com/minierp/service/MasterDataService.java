package com.minierp.service;

import com.minierp.domain.Category;
import com.minierp.domain.Item;
import com.minierp.domain.Location;
import com.minierp.dto.*;
import com.minierp.exception.DuplicateResourceException;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.mapper.MasterDataMapper;
import com.minierp.repository.CategoryRepository;
import com.minierp.repository.ItemRepository;
import com.minierp.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MasterDataService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    public MasterDataService(ItemRepository itemRepository,
                             CategoryRepository categoryRepository,
                             LocationRepository locationRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> listItems() {
        return itemRepository.findAllWithCategory().stream()
                .map(MasterDataMapper::toResponse)
                .toList();
    }

    @Transactional
    public ItemResponse createItem(ItemCreateRequest request) {
        if (itemRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new DuplicateResourceException("Item SKU already exists: " + request.sku());
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));
        String uom = request.unitOfMeasure() == null || request.unitOfMeasure().isBlank()
                ? "PCS" : request.unitOfMeasure();
        Item saved = itemRepository.save(new Item(request.sku(), request.name(), category, uom));
        return MasterDataMapper.toResponse(itemRepository.findWithCategoryById(saved.getId())
                .orElse(saved));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAllByOrderByCodeAsc().stream()
                .map(MasterDataMapper::toResponse)
                .toList();
    }

    @Transactional
    public LocationResponse createLocation(LocationCreateRequest request) {
        if (locationRepository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Location code already exists: " + request.code());
        }
        Location saved = locationRepository.save(new Location(request.code(), request.name()));
        return MasterDataMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(MasterDataMapper::toResponse)
                .toList();
    }
}
