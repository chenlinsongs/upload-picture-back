package com.upload.picture.service;

import com.upload.picture.model.FileSystemItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目录文件列表缓存服务
 */
@Service
public class DirectoryCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectoryCacheService.class);
    
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private static final int MAX_CACHE_SIZE = 50;
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    private static class CacheEntry {
        final List<FileSystemItem> items;
        final long lastModified;
        final long cachedAt;
        
        CacheEntry(List<FileSystemItem> items, long lastModified) {
            this.items = items;
            this.lastModified = lastModified;
            this.cachedAt = System.currentTimeMillis();
        }
        
        boolean isValid(long currentDirModified) {
            if (currentDirModified != lastModified) {
                return false;
            }
            long age = System.currentTimeMillis() - cachedAt;
            return age < CACHE_TTL_MS;
        }
    }
    
    private String getCacheKey(String rootId, String path) {
        return rootId + ":" + path;
    }
    
    public List<FileSystemItem> get(String rootId, String path, File directory) {
        String key = getCacheKey(rootId, path);
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            logger.debug("缓存未命中: {}", key);
            return null;
        }
        
        if (!entry.isValid(directory.lastModified())) {
            logger.info("缓存已失效（目录被修改或过期）: {}", key);
            cache.remove(key);
            return null;
        }
        
        logger.debug("缓存命中: {}, 文件数: {}", key, entry.items.size());
        return entry.items;
    }
    
    public void put(String rootId, String path, List<FileSystemItem> items, File directory) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }
        
        String key = getCacheKey(rootId, path);
        cache.put(key, new CacheEntry(items, directory.lastModified()));
        
        logger.info("缓存目录列表: {}, 文件数: {}", key, items.size());
    }
    
    public void invalidate(String rootId, String path) {
        String key = getCacheKey(rootId, path);
        cache.remove(key);
        logger.info("清除缓存: {}", key);
    }
    
    public void invalidateRoot(String rootId) {
        String prefix = rootId + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
        logger.info("清除根目录缓存: {}", rootId);
    }
    
    public void clear() {
        cache.clear();
        logger.info("清除所有目录缓存");
    }
    
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().cachedAt < oldestTime) {
                oldestTime = entry.getValue().cachedAt;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
            logger.debug("驱逐最旧缓存: {}", oldestKey);
        }
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("size", cache.size());
        stats.put("maxSize", MAX_CACHE_SIZE);
        stats.put("ttlSeconds", CACHE_TTL_MS / 1000);
        return stats;
    }
}
