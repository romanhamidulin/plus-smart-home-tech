package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.api.ShoppingStoreOperations;
import ru.yandex.practicum.dto.shoppindCart.ShoppingCartDto;
import ru.yandex.practicum.dto.shoppingStore.ProductDto;
import ru.yandex.practicum.dto.shoppingStore.QuantityState;
import ru.yandex.practicum.dto.warehouse.*;
import ru.yandex.practicum.exception.*;
import ru.yandex.practicum.mapper.WarehouseMapper;
import ru.yandex.practicum.model.OrderBooking;
import ru.yandex.practicum.model.WarehouseProduct;
import ru.yandex.practicum.repository.BookingRepository;
import ru.yandex.practicum.repository.WarehouseRepository;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final BookingRepository bookingRepository;
    private final WarehouseMapper warehouseMapper;
    private final AddressDto warehouseAddress = initAddress();
    private final ShoppingStoreOperations shoppingStoreClient;

    @Transactional
    @Override
    public void newProductInWarehouse(NewProductInWarehouseRequest request) {
        log.debug("Добавляем новый товар в перечень - {}", request);
        warehouseRepository.findById(request.getProductId())
                .ifPresent(product -> {
                    log.warn("Product with ID: {} already exists", request.getProductId());
                    throw new SpecifiedProductAlreadyInWarehouseException("Product is already in warehouse");
                });
        WarehouseProduct product = warehouseRepository.save(warehouseMapper.toEntity(request));
        log.debug("Добавили товар в перечень - {}", product);
    }

    @Transactional
    @Override
    public BookedProductsDto checkProductQuantityEnoughForShoppingCart(ShoppingCartDto cartDto) {
        log.info("Запрашиваем товары из корзины {}", cartDto);
        Map<UUID, Integer> products = cartDto.getProducts();
        log.info("Запрашиваем количество доступных товаров на складе {}", products.keySet());
        List<WarehouseProduct> availableProductsList = warehouseRepository.findAllById(products.keySet());
        Map<UUID, WarehouseProduct> availableProductsMap = availableProductsList.stream()
                .collect(Collectors.toMap(WarehouseProduct::getProductId, Function.identity()));
        BookedProductsDto bookedProductsDto = new BookedProductsDto();
        for (Map.Entry<UUID, Integer> product : products.entrySet()) {
            UUID id = product.getKey();
            WarehouseProduct availableProduct = availableProductsMap.get(id);
            if (availableProduct == null) {
                throw new NoSpecifiedProductInWarehouseException("Такого товара нет в перечне товаров на складе:" + product.getKey().toString());
            }
            if (availableProduct.getQuantity() >= product.getValue()) {
                Double volume = bookedProductsDto.getDeliveryVolume() + (availableProduct.getWidth() * availableProduct.getHeight() * availableProduct.getDepth()) * product.getValue();
                bookedProductsDto.setDeliveryVolume(volume);
                Double weight = bookedProductsDto.getDeliveryWeight() + (availableProduct.getWeight()) * product.getValue();
                bookedProductsDto.setDeliveryWeight(weight);
                if (availableProduct.getFragile()) {
                    bookedProductsDto.setFragile(true);
                }
            } else {String message = "Количества продукта " + availableProduct.getProductId() + " недостаточно на складе. Уменьшите количество продукта до " + availableProduct.getQuantity();
                log.info(message);
                throw new ProductInShoppingCartLowQuantityInWarehouse(message);
            }
        }
        log.info("Параметры заказа: {}", bookedProductsDto);
        return bookedProductsDto;
    }

    @Transactional
    @Override
    public void addProductToWarehouse(AddProductToWarehouseRequest request) {
        log.info("Запрошено принятие товара на склад {}", request);
        WarehouseProduct product = warehouseRepository.findById(request.getProductId())
                .orElseThrow(() -> new NoSpecifiedProductInWarehouseException("Такого товара нет в перечне товаров на складе:" + request.getProductId()));
        Integer oldQuantity = product.getQuantity();
        Integer newQuantity = oldQuantity + request.getQuantity();
        product.setQuantity(newQuantity);
        warehouseRepository.save(product);
        log.info("Приняли товар на склад");

        log.info("Проверяем, есть ли товар в магазине");
        ProductDto productDto;
        try {
            productDto = shoppingStoreClient.getProduct(product.getProductId());
            QuantityState quantityState = QuantityState.fromQuantity(newQuantity);
            log.info("Обновляем количество товара в магазине");
            shoppingStoreClient.setProductQuantityState(productDto.getProductId(), quantityState);
            log.info("Обновили количество товара в магазине");
        } catch (RuntimeException e) {
            log.info("Такого товара нет в магазине");
        }
    }

    @Override
    public AddressDto getWarehouseAddress() {
        return warehouseAddress;
    }

    private AddressDto initAddress() {
        final String[] addresses = new String[]{"ADDRESS_1", "ADDRESS_2"};
        final String address = addresses[Random.from(new SecureRandom()).nextInt(0, 1)];
        return AddressDto.builder()
                .city(address)
                .street(address)
                .house(address)
                .country(address)
                .flat(address)
                .build();
    }

    @Override
    public BookedProductsDto assemblyProductsForOrder(AssemblyProductsForOrderRequest request) {
        log.info("Собираем товары к заказу {} для передачи в доставку", request.getOrderId());

        Map<UUID, Integer> products = request.getProducts();
        log.info("Запрашиваем количество доступных товаров на складе {}", products.keySet());

        List<WarehouseProduct> availableProductsList = warehouseRepository.findAllById(products.keySet());
        Map<UUID, WarehouseProduct> availableProductsMap = availableProductsList.stream()
                .collect(Collectors.toMap(WarehouseProduct::getProductId, Function.identity()));

        BookedProductsDto bookedProductsDto = new BookedProductsDto();
        List<WarehouseProduct> productsToUpdate = new ArrayList<>();

        for (Map.Entry<UUID, Integer> product : products.entrySet()) {
            UUID id = product.getKey();
            Integer requestedQuantity = product.getValue();
            WarehouseProduct availableProduct = availableProductsMap.get(id);

            validateProductAvailability(availableProduct, id, requestedQuantity);

            updateBookedProductsDto(bookedProductsDto, availableProduct, requestedQuantity);

            availableProduct.setQuantity(availableProduct.getQuantity() - requestedQuantity);
            productsToUpdate.add(availableProduct);

            log.info("Подготовлен к обновлению товар {}: новый остаток {}",
                    availableProduct.getProductId(), availableProduct.getQuantity());
        }

        warehouseRepository.saveAll(productsToUpdate);
        log.info("Товары к заказу {} для передачи в доставку собраны", request.getOrderId());

        createOrderBooking(request);

        log.info("Возвращаем параметры заказа: {}", bookedProductsDto);
        return bookedProductsDto;
    }

    private void validateProductAvailability(WarehouseProduct product, UUID productId, Integer requestedQuantity) {
        if (product == null) {
            throw new NoSpecifiedProductInWarehouseException(
                    "Такого товара нет в перечне товаров на складе: " + productId);
        }

        if (product.getQuantity() < requestedQuantity) {
            String message = String.format(
                    "Количества продукта %s недостаточно на складе. Доступно: %d, запрошено: %d",
                    productId, product.getQuantity(), requestedQuantity);
            log.info(message);
            throw new ProductInShoppingCartLowQuantityInWarehouse(message);
        }
    }

    private void updateBookedProductsDto(BookedProductsDto dto, WarehouseProduct product, Integer quantity) {
        double volume = product.getWidth() * product.getHeight() * product.getDepth() * quantity;
        dto.setDeliveryVolume(dto.getDeliveryVolume() + volume);

        double weight = product.getWeight() * quantity;
        dto.setDeliveryWeight(dto.getDeliveryWeight() + weight);

        if (product.getFragile()) {
            dto.setFragile(true);
        }
    }

    private void createOrderBooking(AssemblyProductsForOrderRequest request) {
        log.info("Создаётся сущность «Забронированные для заказа товары»");
        OrderBooking orderBooking = warehouseMapper.toOrderBooking(request);
        bookingRepository.save(orderBooking);
        log.info("Создана и сохранена в БД сущность «Забронированные для заказа товары» {}", orderBooking);
    }

    @Override
    public void shippedToDelivery(ShippedToDeliveryRequest request) {
        log.info("Запрос на передачу в доставку - {}", request);
        OrderBooking orderBooking = bookingRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new NoOrderFoundException("В базе нет бронирования такого заказа {}" + request.getOrderId()));
        orderBooking.setDeliveryId(request.getDeliveryId());
        bookingRepository.save(orderBooking);
        log.info("Сохранены в БД изменения сущности «Забронированные для заказа товары» {}", orderBooking);
        log.info("Товары переданы в доставку - {}", request);
    }

    @Override
    public void acceptReturn(Map<UUID, Integer> productsToReturn) {
        log.info("Запрошено принятие возврата товаров на склад {}", productsToReturn);

        List<WarehouseProduct> productsToIncreaseList = warehouseRepository.findAllById(productsToReturn.keySet());
        Map<UUID, WarehouseProduct> productsToIncreaseMap = productsToIncreaseList.stream()
                .collect(Collectors.toMap(WarehouseProduct::getProductId, Function.identity()));

        for (Map.Entry<UUID, Integer> productToReturn : productsToReturn.entrySet()) {
            UUID id = productToReturn.getKey();
            WarehouseProduct productToIncrease = productsToIncreaseMap.get(id);

            if (productToIncrease == null) {
                log.error("Товар с id {} не найден на складе", id);
                throw new ProductNotFoundException("Товар не найден на складе: " + id);
            }

            Integer oldQuantity = productToIncrease.getQuantity();
            Integer newQuantity = oldQuantity + productsToReturn.get(id);
            productToIncrease.setQuantity(newQuantity);
            log.info("Приняли возврат товара {} на склад. Новое количество: {}", id, newQuantity);

            updateProductInStore(productToIncrease.getProductId(), newQuantity);
        }

        warehouseRepository.saveAll(productsToIncreaseList);
        log.info("Все возвраты успешно обработаны");
    }

    private void updateProductInStore(UUID productId, Integer newQuantity) {
        log.info("Проверяем, есть ли товар {} в магазине", productId);
        try {
            ProductDto productDto = shoppingStoreClient.getProduct(productId);
            QuantityState quantityState = QuantityState.fromQuantity(newQuantity);

            log.info("Обновляем количество товара в магазине на {}", quantityState);
            shoppingStoreClient.setProductQuantityState(productDto.getProductId(), quantityState);
            log.info("Обновили количество товара в магазине");
        } catch (RuntimeException e) {
            log.warn("Товара {} нет в магазине или ошибка при обновлении", productId, e);
        }
    }
}