package io.rj.rwhite;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class DownloadObserver {

    private final BlockingQueue<DownloadMessage> queue = new LinkedBlockingQueue<>();
    private final List<DownloadCallback> callbacks = new CopyOnWriteArrayList<>();

    DownloadObserver() {
        Thread thread = new Thread(() -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    DownloadMessage message = queue.take();
                    while (callbacks.size() < 1) {
                        Thread.sleep(100);
                    }
                    for (DownloadCallback callback : callbacks) {
                        callback.call(message);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void sendMessage(DownloadMessage message) {
        try {
            queue.put(message);
        } catch (InterruptedException e) {

        }
    }

    public boolean register(DownloadCallback callback) {
        return callbacks.add(callback);
    }

    public boolean unregister(DownloadCallback callback) {
        return callbacks.remove(callback);
    }

}
