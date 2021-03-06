package com.github.fuchsnj;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;

/**
 * A buffer that buffers individual data, collecting it in bucket by key, then running a task to batch
 * process the data
 *
 * @param <K> Key
 * @param <D> Data
 */
public class KeyedTaskBuffer<K, D> {
    private Timer timer = new Timer();
    private final int maxBufferSize;
    private final Duration maxWait;
    private final Map<K, List<D>> buffers = new HashMap<>();
    private final BiConsumer<K, List<D>> task;

    public KeyedTaskBuffer(int maxBufferSize, Duration maxWait, BiConsumer<K, List<D>> task) {
        this.maxBufferSize = maxBufferSize;
        this.maxWait = maxWait;
        this.task = task;
    }

    public synchronized void addData(K key, D data) {
        List<D> buffer = buffers.get(key);
        if (buffer == null) {
            TimerTask timerTask = new TimerExpiredTask(key);
            buffer = new ArrayList<>();
            buffers.put(key, buffer);
            timer.schedule(timerTask, maxWait.toMillis());
        }
        buffer.add(data);
        processBatchIfNeeded(key, false);
    }

    private synchronized void processBatchIfNeeded(K key, boolean expired) {
        if (!buffers.containsKey(key)) {
            return;
        }
        List<D> buffer = buffers.get(key);
        boolean isMaxSize = buffer.size() >= maxBufferSize;
        if (isMaxSize || expired) {
            buffers.remove(key);
            task.accept(key, buffer);
        }
    }

    /**
     * Internal task that runs when data in a buffers has exceeded 'maxWaitMillis'
     */
    private class TimerExpiredTask extends TimerTask {
        private final K key;

        public TimerExpiredTask(K key) {
            this.key = key;
        }

        @Override
        public void run() {
            processBatchIfNeeded(key, true);
        }
    }

    void setTimer(Timer timer) {
        this.timer = timer;
    }
}
