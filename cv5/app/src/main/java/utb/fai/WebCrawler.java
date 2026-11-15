package utb.fai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class WebCrawler {

    private static final int MAX_SIMULTANEOUS_DOWNLOADS = 16;

    private final int maxDepth;
    private final int debugLevel;
    private final WordFrequency wordFrequency;
    private final Set<String> visitedURIs = ConcurrentHashMap.newKeySet();
    private final PendingTasks pendingTasks = new PendingTasks();
    private final Semaphore downloadPermits = new Semaphore(MAX_SIMULTANEOUS_DOWNLOADS);
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final Executor threadPerUrlExecutor = this::startNewThread;

    public WebCrawler(int maxDepth, int debugLevel, WordFrequency wordFrequency) {
        this.maxDepth = maxDepth;
        this.debugLevel = debugLevel;
        this.wordFrequency = wordFrequency;
    }

    public void crawl(String startUrl) throws InterruptedException {
        URI startUri = normalize(startUrl);
        if (startUri == null) {
            System.err.println("Invalid start URL: " + startUrl);
            return;
        }
        schedule(startUri, 0);
        pendingTasks.awaitAllFinished();
        debug(1, "Finished, visited " + visitedURIs.size() + " pages");
    }

    void schedule(URI uri, int depth) {
        if (depth > maxDepth || !markVisited(uri)) {
            return;
        }
        pendingTasks.started();
        try {
            threadPerUrlExecutor.execute(new PageTask(this, new URIinfo(uri, depth)));
        } catch (RuntimeException | Error e) {
            pendingTasks.finished();
            System.err.println("Unable to start thread for " + uri + ": " + e);
        }
    }

    private void startNewThread(Runnable pageTask) {
        Thread thread = new Thread(() -> {
            try {
                pageTask.run();
            } finally {
                pendingTasks.finished();
            }
        }, "crawler-" + threadCounter.incrementAndGet());
        thread.start();
    }

    boolean markVisited(URI uri) {
        return visitedURIs.add(uri.toString());
    }

    boolean shouldFollowLinksFrom(int depth) {
        return depth < maxDepth;
    }

    void acquireDownloadPermit() throws InterruptedException {
        downloadPermits.acquire();
    }

    void releaseDownloadPermit() {
        downloadPermits.release();
    }

    WordFrequency wordFrequency() {
        return wordFrequency;
    }

    void debug(int level, String message) {
        if (debugLevel >= level) {
            System.err.println("[" + Thread.currentThread().getName() + "] " + message);
        }
    }

    static URI normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return new URI(scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + port + path + query);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
