package com.devjefster.microcache;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class MicroCache<K, V> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MicroCache.class.getName());
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final LinkedHashMap<K, V> lruCache;
    private transient final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private transient final ExecutorService asyncLoader = Executors.newCachedThreadPool();
    private transient final Consumer<K> expiryCallback;
    private transient final Function<K, V> asyncLoaderFunction;
    private final String persistenceFilePath;
    private final boolean persistenceEnabled;
    private final int maxSize;

    public MicroCache(Consumer<K> expiryCallback, Function<K, V> asyncLoaderFunction, int maxSize, String persistenceFilePath) {
        this.expiryCallback = expiryCallback;
        this.asyncLoaderFunction = asyncLoaderFunction;
        this.maxSize = maxSize;
        this.persistenceFilePath = persistenceFilePath;
        this.lruCache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
        this.persistenceEnabled = false;
    }
    public MicroCache(Consumer<K> expiryCallback, Function<K, V> asyncLoaderFunction, int maxSize, String persistenceFilePath, boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
        this.expiryCallback = expiryCallback;
        this.asyncLoaderFunction = asyncLoaderFunction;
        this.maxSize = maxSize;
        this.persistenceFilePath = persistenceFilePath;
        this.lruCache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
        if (persistenceEnabled) {
            loadFromDisk();
        }
    }

    public void put(K key, V value, long ttlMillis) {
        synchronized (lruCache) {
            if (lruCache.size() >= maxSize) {
                Iterator<K> iterator = lruCache.keySet().iterator();
                if (iterator.hasNext()) {
                    K eldestKey = iterator.next();
                    lruCache.remove(eldestKey);
                    cache.remove(eldestKey);
                }
            }
            lruCache.put(key, value);
        }
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        cache.put(key, new CacheEntry<>(value, expiryTime));
        scheduleCleanup(key, ttlMillis);
        if (persistenceEnabled) {
            saveToDisk();
        }
    }

    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && System.currentTimeMillis() < entry.expiryTime) {
            synchronized (lruCache) {
                lruCache.put(key, entry.value);
            }
            return entry.value;
        }
        cache.remove(key);
        return loadAsync(key);
    }

    private V loadAsync(K key) {
        CompletableFuture<V> future = CompletableFuture.supplyAsync(() -> asyncLoaderFunction.apply(key), asyncLoader);
        try {
            return future.get();
        } catch (Exception e) {
            LOGGER.warning("Async loading failed for key: " + key);
            return null;
        }
    }

    public void remove(K key) {
        cache.remove(key);
        synchronized (lruCache) {
            lruCache.remove(key);
        }
        saveToDisk();
    }

    public void clear() {
        cache.clear();
        synchronized (lruCache) {
            lruCache.clear();
        }
        saveToDisk();
    }

    public int size() {
        return cache.size();
    }

    private void scheduleCleanup(K key, long ttlMillis) {
        if (!persistenceEnabled) return;
        cleaner.schedule(() -> {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && System.currentTimeMillis() >= entry.expiryTime) {
                cache.remove(key);
                synchronized (lruCache) {
                    lruCache.remove(key);
                }
                expiryCallback.accept(key);
                LOGGER.info("Entry expired and removed: " + key);
                saveToDisk();
            }
        }, ttlMillis, TimeUnit.MILLISECONDS);
    }

    private void saveToDisk() {
        if (!persistenceEnabled) return;
        Path persistenceFile = Paths.get(persistenceFilePath);
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(persistenceFile))) {
            out.writeObject(cache);
            out.writeObject(lruCache);
        } catch (IOException e) {
            LOGGER.warning("Failed to save cache to disk: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!persistenceEnabled) return;
        Path persistenceFile = Paths.get(persistenceFilePath);
        if (Files.exists(persistenceFile)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(persistenceFile))) {
                Map<K, CacheEntry<V>> loadedCache = (Map<K, CacheEntry<V>>) in.readObject();
                cache.putAll(loadedCache);
                Map<K, V> loadedLruCache = (Map<K, V>) in.readObject();
                synchronized (lruCache) {
                    lruCache.putAll(loadedLruCache);
                }
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.warning("Failed to load cache from disk: " + e.getMessage());
            }
        }
    }

    private static class CacheEntry<V> implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        final V value;
        final long expiryTime;

        CacheEntry(V value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }


}
