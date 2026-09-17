package com.corebank.performance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
class BalancePerformanceTest {
    @Test
    void shouldMeasureBalanceEndpoint() throws Exception {
        String baseUrl = System.getProperty("performance.baseUrl", "http://localhost:8080");
        int concurrency = Integer.getInteger("performance.concurrency", 50);
        int requests = Integer.getInteger("performance.requests", 1000);
        String accountId = System.getProperty("performance.accountId", "00000000-0000-0000-0000-000000000001");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/accounts/" + accountId + "/balance"))
                .timeout(Duration.ofSeconds(5)).GET().build();

        AtomicLong success = new AtomicLong();
        Semaphore semaphore = new Semaphore(concurrency);
        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                        if (response.statusCode() == 200) success.incrementAndGet();
                    } catch (Exception ignored) { } finally {
                        semaphore.release();
                    }
                }));
            }
            for (var f : futures) f.get();
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        double throughput = success.get() / seconds;
        System.out.printf("Performance: success=%d/%d, duration=%.2fs, throughput=%.2f req/s, concurrency=%d%n",
                success.get(), requests, seconds, throughput, concurrency);
        assertTrue(success.get() > 0, "Application must be running for performance test");
    }
}
