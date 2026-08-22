package com.minierp.controller;

import com.minierp.dto.*;
import com.minierp.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Master Data", description = "Items, locations and categories")
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @Operation(summary = "List items. Any authenticated role.")
    @GetMapping("/items")
    public List<ItemResponse> items() {
        return masterDataService.listItems();
    }

    @Operation(summary = "Create an item. Requires ADMIN.")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ItemResponse createItem(@Valid @RequestBody ItemCreateRequest request) {
        return masterDataService.createItem(request);
    }

    @Operation(summary = "List locations. Any authenticated role.")
    @GetMapping("/locations")
    public List<LocationResponse> locations() {
        return masterDataService.listLocations();
    }

    @Operation(summary = "Create a location. Requires ADMIN.")
    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LocationResponse createLocation(@Valid @RequestBody LocationCreateRequest request) {
        return masterDataService.createLocation(request);
    }

    @Operation(summary = "List categories. Any authenticated role.")
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return masterDataService.listCategories();
    }
}
