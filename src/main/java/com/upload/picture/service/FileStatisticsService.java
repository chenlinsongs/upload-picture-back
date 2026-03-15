package com.upload.picture.service;

import java.io.File;
import java.util.*;

/**
 * 文件统计服务
 * 负责统计上传文件的数量
 */
public class FileStatisticsService {
    
    public int countImages(String imageDir) {
        File dir = new File(imageDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                   lower.endsWith(".png") || lower.endsWith(".gif") || 
                   lower.endsWith(".bmp") || lower.endsWith(".webp") ||
                   lower.endsWith(".heic") || lower.endsWith(".heif");
        });
        
        return files != null ? files.length : 0;
    }
    
    public int countVideos(String videoDir) {
        File dir = new File(videoDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mov") || lower.endsWith(".mp4") || 
                   lower.endsWith(".avi") || lower.endsWith(".mkv");
        });
        
        return files != null ? files.length : 0;
    }
    
    public Map<String, Integer> countLivePhotos(String livePhotoDir) {
        Map<String, Integer> result = new HashMap<>();
        result.put("pairs", 0);
        result.put("images", 0);
        result.put("videos", 0);
        result.put("total", 0);
        
        File dir = new File(livePhotoDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }
        
        Set<String> imageBasenames = new HashSet<>();
        Set<String> videoBasenames = new HashSet<>();
        
        File[] allFiles = dir.listFiles();
        if (allFiles == null) {
            return result;
        }
        
        for (File file : allFiles) {
            if (file.isFile()) {
                String fileName = file.getName();
                String lowerName = fileName.toLowerCase();
                String baseName = getBaseName(fileName);
                
                if (lowerName.endsWith(".heic") || lowerName.endsWith(".heif") ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png")) {
                    imageBasenames.add(baseName);
                } else if (lowerName.endsWith(".mov") || lowerName.endsWith(".mp4")) {
                    videoBasenames.add(baseName);
                }
            }
        }
        
        Set<String> pairedNames = new HashSet<>(imageBasenames);
        pairedNames.retainAll(videoBasenames);
        
        result.put("pairs", pairedNames.size());
        result.put("images", imageBasenames.size());
        result.put("videos", videoBasenames.size());
        result.put("total", imageBasenames.size() + videoBasenames.size());
        
        return result;
    }
    
    private String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    public Map<String, Object> getAllStatistics(String uploadBaseDir) {
        Map<String, Object> stats = new HashMap<>();
        
        String imageDir = uploadBaseDir + "images/";
        String videoDir = uploadBaseDir + "videos/";
        String livePhotoDir = uploadBaseDir + "livephotos/";
        
        stats.put("images", countImages(imageDir));
        stats.put("videos", countVideos(videoDir));
        stats.put("livePhotos", countLivePhotos(livePhotoDir));
        
        return stats;
    }
}
