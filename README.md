# MicroCache

## Overview
MicroCache is a lightweight, in-memory caching library for Java with TTL (Time-To-Live), LRU (Least Recently Used) eviction, asynchronous loading, and optional disk persistence.

## Features
- ✅ **Thread-safe** caching using `ConcurrentHashMap`.
- ⏳ **TTL Support** - Set expiration times for cached values.
- ♻️ **LRU Eviction** - Automatically removes least recently used items when cache is full.
- 🚀 **Async Data Loading** - Fetch missing values asynchronously.
- 💾 **Persistence Option** - Save and reload cache from disk.
- 📊 **Memory Usage Monitoring** - Track cache impact on memory.

## Installation
Clone the repository and include the `MicroCache.java` file in your project.

## Usage
### Creating a Cache
```java
MicroCache<String, String> cache = new MicroCache<>(
    key -> System.out.println("Expired: " + key),
    key -> "Loaded async value for " + key,
    100,  // Max size
    "cache_data.ser",  // Persistence file
    true  // Enable persistence
);
```

### Adding and Retrieving Values
```java
cache.put("key1", "value1", 5000); // Store value with 5s TTL
System.out.println(cache.get("key1")); // Retrieves value
```

### Expiration Handling
```java
Thread.sleep(6000);
System.out.println(cache.get("key1")); // Should return null (expired)
```

### Performance Testing
```java
long start = System.nanoTime();
for (int i = 0; i < 100000; i++) {
    cache.put("key" + i, "value" + i, 5000);
}
long end = System.nanoTime();
System.out.println("Insertion time: " + (end - start) / 1_000_000 + " ms");
System.out.println("Memory usage: " + MicroCache.getMemoryUsage() + " MB");
```

## Configuration Options
| Parameter | Description |
|-----------|-------------|
| `maxSize` | Max cache capacity before eviction occurs |
| `ttlMillis` | Time-to-live for cache entries (ms) |
| `persistenceEnabled` | Enable/disable disk persistence |
| `asyncLoaderFunction` | Function to load missing values asynchronously |

## License
MIT License. Free to use and modify.

## Contributing
Feel free to submit PRs or report issues!

