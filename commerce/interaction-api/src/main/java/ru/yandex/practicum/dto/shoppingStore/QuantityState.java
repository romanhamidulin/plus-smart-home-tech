package ru.yandex.practicum.dto.shoppingStore;

public enum QuantityState {
    ENDED,  // Товар закончился
    FEW,    // Осталось меньше 10 единиц товара
    ENOUGH, // Осталось от 10 до 100 единиц
    MANY;    // Осталось больше 100 единиц

    public static QuantityState fromQuantity(Integer quantity) {
        if (quantity == null) {
            return ENDED;
        }
        if (quantity > 100) {
            return MANY;
        } else if (quantity > 10) {
            return ENOUGH;
        } else if (quantity > 0) {
            return FEW;
        } else {
            return ENDED;
        }
    }
}