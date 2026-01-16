package ru.yandex.practicum;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.processors.HubEventProcessor;
import ru.yandex.practicum.processors.SnapshotProcessor;

@Component
@RequiredArgsConstructor
public class AnalyzerRunner implements CommandLineRunner {
    final HubEventProcessor hubEventProcessor;
    final SnapshotProcessor snapshotProcessor;

    @Override
    public void run(String... args) throws Exception {
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        snapshotProcessor.start();
    }


}