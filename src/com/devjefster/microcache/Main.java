package com.devjefster.microcache;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        MicroCache<String, String> cache = new MicroCache<>(
                key -> System.out.println("Expired: " + key),
                key -> "Loaded async value for " + key,
                100,
                "cache_data.ser"
        );

        cache.put("test", "Hello World", 3000);
        System.out.println("Value: " + cache.get("test"));
        Thread.sleep(4000);
        System.out.println("Value after expiration: " + cache.get("test"));
        System.out.println("Cache size: " + cache.size());

        // Performance Test with Memory Usage
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            cache.put("key" + i, "value" + i, 5000);
        }
        long end = System.nanoTime();
        System.out.println("Time taken to insert 100,000 elements: " + (end - start) / 1_000_000 + " ms");
        System.out.println("Memory usage after insertion: " + getMemoryUsage() + " MB");
    }

    public static long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}
