package ru.yandex.practicum.telemetry.collector.service.sensor;

import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEventType;

public interface SensorEventHandler {
    SensorEventProto.PayloadCase getMessageType();
    void handle(SensorEventProto event);
}
