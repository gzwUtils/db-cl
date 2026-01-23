package db.cl.gao.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存包装器，提供类似Caffeine的接口
 */
@SuppressWarnings("unused")
public class LocalCacheWrapper<K, V> {

    private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 缓存统计
    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder evictionCount = new LongAdder();

    public LocalCacheWrapper() {
        // 定时清理过期条目
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 放入缓存（带过期时间）
     */
    public void put(K key, V value, long ttlMillis) {
        CacheEntry<V> entry = new CacheEntry<>(value, ttlMillis);
        cache.put(key, entry);
    }

    /**
     * 放入缓存（使用默认过期时间）
     */
    public void put(K key, V value) {
        put(key, value, 300000); // 默认5分钟
    }

    /**
     * 获取缓存值
     */
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            missCount.increment();
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            evictionCount.increment();
            missCount.increment();
            return null;
        }

        hitCount.increment();
        return entry.getValue();
    }

    /**
     * 获取缓存值（如果存在）
     */
    public V getIfPresent(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.getValue();
    }

    /**
     * 移除缓存
     */
    public void invalidate(K key) {
        cache.remove(key);
    }

    /**
     * 清空所有缓存
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     */
    public long estimatedSize() {
        return cache.size();
    }

    /**
     * 获取缓存统计信息
     */
    public String stats() {
        long hits = hitCount.sum();
        long misses = missCount.sum();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        return String.format("Cache{size=%d, hits=%d, misses=%d, hitRate=%.1f%%, evictions=%d}",
                cache.size(), hits, misses, hitRate, evictionCount.sum());
    }

    /**
     * 清理过期缓存
     */
    private void cleanupExpired() {
        long count = 0;
        for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
                count++;
            }
        }
        if (count > 0) {
            evictionCount.add(count);
        }
    }

    /**
     * 关闭清理线程
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry<V> {
        private final V value;
        private final long expireTime;

        CacheEntry(V value, long ttlMillis) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        V getValue() {
            return value;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
