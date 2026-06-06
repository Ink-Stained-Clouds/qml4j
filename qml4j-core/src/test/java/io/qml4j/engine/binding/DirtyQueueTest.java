package io.qml4j.engine.binding;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyQueueTest {

    @Test
    void deduplicatesAndFlushesInOrder() {
        DirtyQueue q = new DirtyQueue();
        List<String> log = new ArrayList<>();
        Runnable a = () -> log.add("a");
        Runnable b = () -> log.add("b");
        q.enqueue(a);
        q.enqueue(b);
        q.enqueue(a);
        q.flush();
        assertEquals(List.of("a", "b"), log);
        assertTrue(q.isEmpty());
    }

    @Test
    void reentrantEnqueueProcessed() {
        DirtyQueue q = new DirtyQueue();
        List<String> log = new ArrayList<>();
        q.enqueue(() -> {
            log.add("first");
            q.enqueue(() -> log.add("second"));
        });
        q.flush();
        assertEquals(List.of("first", "second"), log);
    }
}
