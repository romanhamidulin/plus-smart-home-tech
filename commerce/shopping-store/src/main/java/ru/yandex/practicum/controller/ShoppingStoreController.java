package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.api.ShoppingStoreOperations;
import ru.yandex.practicum.dto.shoppingStore.ProductCategory;
import ru.yandex.practicum.dto.shoppingStore.SetProductQuantityStateRequest;
import ru.yandex.practicum.service.ShoppingStoreService;
import ru.yandex.practicum.dto.shoppingStore.ProductDto;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/shopping-store")
public class ShoppingStoreController implements ShoppingStoreOperations {
    private final ShoppingStoreService shoppingStoreService;

    @Override
    @GetMapping
    public Page<ProductDto> getProducts(@RequestParam(required = false) ProductCategory category,
                                        Pageable pageable) {
        log.info("GET /api/v1/shopping-store - Получение списка товаров: category={}, pageable={}",
                category, pageable);
        Page<ProductDto> response = shoppingStoreService.getProducts(category, pageable);
        log.info("Возвращаем страницу товаров: totalElements={}", response.getTotalElements());
        return response;
    }

    @Override
    @PostMapping
    public ProductDto createNewProduct(@RequestBody @Valid ProductDto productDto) {
        log.info("POST /api/v1/shopping-store - Добавление товара: {}", productDto);
        ProductDto response = shoppingStoreService.addProduct(productDto);
        log.info("Возвращаем товар: {}", response);
        return response;
    }

    @Override
    @PutMapping
    public ProductDto updateProduct(@RequestBody @Valid ProductDto productDto) {
        log.info("PUT /api/v1/shopping-store - Обновление товара: {}", productDto);
        ProductDto response = shoppingStoreService.updateProduct(productDto);
        log.info("Возвращаем товар: {}", response);
        return response;
    }

    @Override
    @PostMapping("/removeProductFromStore")
    public boolean removeProductFromStore(@RequestBody @Valid UUID productId) {
        log.info("POST /api/v1/shopping-store/removeProductFromStore - Удаление товара: {}", productId);
        boolean response = shoppingStoreService.removeProduct(productId);
        log.info("Удалили товар: {}", response);
        return response;
    }

    @Override
    @PostMapping("/quantityState")
    public boolean setProductQuantityState(@RequestBody @Valid SetProductQuantityStateRequest request) {
        log.info("POST /api/v1/shopping-store/quantityState - Обновление количества товара {}: {}",
                request.getProductId(), request.getQuantityState());
        boolean response = shoppingStoreService.updateQuantityState(request);
        log.info("Обновили количество товаров: {}", response);
        return response;
    }

    @Override
    @GetMapping("/{productId}")
    public ProductDto getProduct(@PathVariable UUID productId) {
        log.info("GET /api/v1/shopping-store/{} - Получение товара", productId);
        ProductDto response = shoppingStoreService.getProductById(productId);
        log.info("Возвращаем товар: {}", response);
        return response;
    }
}