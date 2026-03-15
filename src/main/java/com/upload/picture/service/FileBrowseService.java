package com.upload.picture.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.upload.picture.model.FileSystemItem;
import com.upload.picture.model.MediaRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.Base64;

/**
 * 文件浏览服务
 */
@Service
public class FileBrowseService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileBrowseService.class);
    
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
    ));
    
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mov", "mp4", "avi", "mkv", "m4v"
    ));
    
    @Autowired
    private com.upload.picture.config.MediaBrowseConfig mediaBrowseConfig;
    
    @Autowired
    private DirectoryCacheService directoryCacheService;
    
    public Map<String, Object> browse(String rootId, String relativePath, boolean includeDetails) {
        return browse(rootId, relativePath, includeDetails, -1, 50);
    }
    
    public Map<String, Object> browse(String rootId, String relativePath, boolean includeDetails, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            MediaRoot root = mediaBrowseConfig.getRootById(rootId);
            if (root == null) {
                result.put("success", false);
                result.put("error", "根目录不存在: " + rootId);
                return result;
            }
            
            if (!mediaBrowseConfig.isPathAllowed(rootId, relativePath)) {
                result.put("success", false);
                result.put("error", "路径不允许访问");
                return result;
            }
            
            String fullPath = mediaBrowseConfig.getFullPath(rootId, relativePath);
            if (fullPath == null) {
                result.put("success", false);
                result.put("error", "无法解析路径");
                return result;
            }
            
            File targetDir = new File(fullPath);
            if (!targetDir.exists()) {
                result.put("success", false);
                result.put("error", "目录不存在: " + relativePath);
                return result;
            }
            
            if (!targetDir.isDirectory()) {
                result.put("success", false);
                result.put("error", "不是有效的目录");
                return result;
            }
            
            String parentPath = null;
            if (!relativePath.equals("/") && !relativePath.isEmpty()) {
                Path path = Paths.get(relativePath);
                Path parent = path.getParent();
                parentPath = parent != null ? parent.toString() : "/";
                if (parentPath.isEmpty()) {
                    parentPath = "/";
                }
            }
            
            List<FileSystemItem> allItems = directoryCacheService.get(rootId, relativePath, targetDir);
            
            if (allItems == null) {
                long startTime = System.currentTimeMillis();
                allItems = listFiles(targetDir, rootId, relativePath, includeDetails);
                long elapsed = System.currentTimeMillis() - startTime;
                
                logger.info("文件系统读取耗时: {}ms, 文件数: {}, 路径: {}", elapsed, allItems.size(), relativePath);
                
                directoryCacheService.put(rootId, relativePath, allItems, targetDir);
            } else {
                logger.info("从缓存获取文件列表: {} 个文件, 路径: {}", allItems.size(), relativePath);
            }
            
            int totalItems = allItems.size();
            int totalPages = page >= 0 ? (int) Math.ceil((double) totalItems / pageSize) : 1;
            boolean hasMore = false;
            List<FileSystemItem> items;
            
            if (page >= 0) {
                int startIndex = page * pageSize;
                int endIndex = Math.min(startIndex + pageSize, totalItems);
                
                if (startIndex >= totalItems) {
                    items = new ArrayList<>();
                } else {
                    items = allItems.subList(startIndex, endIndex);
                }
                
                hasMore = endIndex < totalItems;
            } else {
                items = allItems;
            }
            
            result.put("success", true);
            result.put("rootId", rootId);
            result.put("rootName", root.getName());
            result.put("currentPath", relativePath);
            result.put("parentPath", parentPath);
            result.put("isRoot", relativePath.equals("/") || relativePath.isEmpty());
            result.put("items", items);
            
            result.put("totalItems", totalItems);
            result.put("totalPages", totalPages);
            result.put("currentPage", page >= 0 ? page : 0);
            result.put("pageSize", pageSize);
            result.put("hasMore", hasMore);
            
        } catch (Exception e) {
            logger.error("浏览目录失败", e);
            result.put("success", false);
            result.put("error", "浏览失败: " + e.getMessage());
        }
        
        return result;
    }
    
    private List<FileSystemItem> listFiles(File directory, String rootId, String currentPath, boolean includeDetails) {
        List<FileSystemItem> items = new ArrayList<>();
        
        File[] files = directory.listFiles();
        if (files == null) {
            return items;
        }
        
        Map<String, File> imageFiles = new HashMap<>();
        Map<String, File> videoFiles = new HashMap<>();
        Set<String> livePhotoNames = new HashSet<>();
        List<File> folders = new ArrayList<>();
        
        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            
            if (file.isDirectory()) {
                folders.add(file);
            } else {
                String name = file.getName();
                String extension = getFileExtension(name);
                String baseName = getFileBaseName(name);
                
                if (isImageFile(extension)) {
                    imageFiles.put(baseName.toLowerCase(), file);
                } else if (isVideoFile(extension)) {
                    videoFiles.put(baseName.toLowerCase(), file);
                }
            }
        }
        
        for (String baseName : imageFiles.keySet()) {
            if (videoFiles.containsKey(baseName)) {
                livePhotoNames.add(baseName);
            }
        }
        
        for (File folder : folders) {
            try {
                FileSystemItem item = createFileSystemItem(folder, rootId, currentPath, includeDetails);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                logger.warn("创建文件夹项失败: {}", folder.getName(), e);
            }
        }
        
        for (String baseName : livePhotoNames) {
            File imageFile = imageFiles.get(baseName);
            File videoFile = videoFiles.get(baseName);
            
            try {
                FileSystemItem item = createLivePhotoItem(imageFile, videoFile, rootId, currentPath, includeDetails);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                logger.warn("创建 Live Photo 项失败: {}", baseName, e);
            }
        }
        
        for (Map.Entry<String, File> entry : imageFiles.entrySet()) {
            if (!livePhotoNames.contains(entry.getKey())) {
                try {
                    FileSystemItem item = createFileSystemItem(entry.getValue(), rootId, currentPath, includeDetails);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    logger.warn("创建图片项失败: {}", entry.getValue().getName(), e);
                }
            }
        }
        
        for (Map.Entry<String, File> entry : videoFiles.entrySet()) {
            if (!livePhotoNames.contains(entry.getKey())) {
                try {
                    FileSystemItem item = createFileSystemItem(entry.getValue(), rootId, currentPath, includeDetails);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    logger.warn("创建视频项失败: {}", entry.getValue().getName(), e);
                }
            }
        }
        
        items.sort((a, b) -> {
            if (a.getType().equals("folder") && !b.getType().equals("folder")) {
                return -1;
            } else if (!a.getType().equals("folder") && b.getType().equals("folder")) {
                return 1;
            } else {
                return Long.compare(b.getModifiedDate(), a.getModifiedDate());
            }
        });
        
        return items;
    }
    
    private FileSystemItem createLivePhotoItem(File imageFile, File videoFile, String rootId, String currentPath, boolean includeDetails) throws IOException {
        FileSystemItem item = new FileSystemItem();
        
        String baseName = getFileBaseName(imageFile.getName());
        item.setName(baseName);
        item.setType("livephoto");
        
        BasicFileAttributes attrs = Files.readAttributes(imageFile.toPath(), BasicFileAttributes.class);
        item.setModifiedDate(imageFile.lastModified());
        item.setCreationDate(attrs.creationTime().toMillis());
        
        item.setFileSize(imageFile.length() + videoFile.length());
        
        String imagePath;
        String videoPath;
        if (currentPath.equals("/") || currentPath.isEmpty()) {
            imagePath = "/" + imageFile.getName();
            videoPath = "/" + videoFile.getName();
        } else {
            String normalizedCurrent = currentPath.startsWith("/") ? currentPath : "/" + currentPath;
            if (normalizedCurrent.endsWith("/") && !normalizedCurrent.equals("/")) {
                normalizedCurrent = normalizedCurrent.substring(0, normalizedCurrent.length() - 1);
            }
            imagePath = normalizedCurrent + "/" + imageFile.getName();
            videoPath = normalizedCurrent + "/" + videoFile.getName();
        }
        
        item.setId(imagePath);
        item.setPath(baseName);
        
        item.setThumbnailUrl("/image/thumbnail?rootId=" + rootId + "&fileId=" + urlEncode(imagePath));
        item.setFullUrl("/image/download?rootId=" + rootId + "&fileId=" + urlEncode(imagePath));
        item.setVideoUrl("/image/download?rootId=" + rootId + "&fileId=" + urlEncode(videoPath));
        
        item.setImageFileName(imageFile.getName());
        item.setVideoFileName(videoFile.getName());
        
        return item;
    }
    
    private String getFileBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    private FileSystemItem createFileSystemItem(File file, String rootId, String currentPath, boolean includeDetails) throws IOException {
        FileSystemItem item = new FileSystemItem();
        
        item.setName(file.getName());
        
        BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        item.setModifiedDate(file.lastModified());
        
        String itemPath;
        if (currentPath.equals("/") || currentPath.isEmpty()) {
            itemPath = "/" + file.getName();
        } else {
            String normalizedCurrent = currentPath.startsWith("/") ? currentPath : "/" + currentPath;
            if (normalizedCurrent.endsWith("/") && !normalizedCurrent.equals("/")) {
                normalizedCurrent = normalizedCurrent.substring(0, normalizedCurrent.length() - 1);
            }
            itemPath = normalizedCurrent + "/" + file.getName();
        }
        item.setPath(itemPath);
        item.setId(itemPath);
        
        if (file.isDirectory()) {
            item.setType("folder");
            
            int[] stats = calculateFolderStats(file);
            item.setItemCount(stats[0]);
            item.setTotalSize((long) stats[1]);
            
        } else {
            String extension = getFileExtension(file.getName());
            item.setFileSize(file.length());
            item.setCreationDate(attrs.creationTime().toMillis());
            
            if (isImageFile(extension)) {
                item.setType("image");
                
                if (includeDetails) {
                    try {
                        BufferedImage img = ImageIO.read(file);
                        if (img != null) {
                            item.setWidth(img.getWidth());
                            item.setHeight(img.getHeight());
                        }
                    } catch (Exception e) {
                        logger.debug("无法读取图片尺寸: {}", file.getName());
                    }
                    
                    item.setHasEXIF(hasExif(file));
                }
                
                item.setThumbnailUrl("/image/thumbnail?rootId=" + rootId + "&fileId=" + urlEncode(itemPath));
                item.setFullUrl("/image/download?rootId=" + rootId + "&fileId=" + urlEncode(itemPath));
                
            } else if (isVideoFile(extension)) {
                item.setType("video");
                
                item.setThumbnailUrl("/image/thumbnail?rootId=" + rootId + "&fileId=" + urlEncode(itemPath));
                item.setFullUrl("/image/download?rootId=" + rootId + "&fileId=" + urlEncode(itemPath));
                
            } else {
                return null;
            }
            
            item.setPath(file.getName());
        }
        
        return item;
    }
    
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
    
    private String decodeFileId(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return fileId;
        }
        
        if (fileId.startsWith("/")) {
            return fileId;
        }
        
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(fileId);
            return new String(decoded, "UTF-8");
        } catch (Exception e) {
            // try URL decode
        }
        
        try {
            return java.net.URLDecoder.decode(fileId, "UTF-8");
        } catch (Exception e) {
            return fileId;
        }
    }
    
    private int[] calculateFolderStats(File folder) {
        int count = 0;
        long size = 0;
        
        File[] files = folder.listFiles();
        if (files == null) {
            return new int[]{0, 0};
        }
        
        Set<String> processedLivePhotoBaseNames = new HashSet<>();
        Map<String, File> imageFiles = new HashMap<>();
        Map<String, File> movFiles = new HashMap<>();
        
        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            
            String name = file.getName();
            String lowerName = name.toLowerCase();
            
            if (file.isFile()) {
                String baseName = getBaseName(name);
                
                if (lowerName.endsWith(".heic") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    imageFiles.put(baseName.toLowerCase(), file);
                } else if (lowerName.endsWith(".mov")) {
                    movFiles.put(baseName.toLowerCase(), file);
                }
            }
        }
        
        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            
            String name = file.getName();
            String lowerName = name.toLowerCase();
            String baseName = getBaseName(name).toLowerCase();
            
            if (file.isFile()) {
                size += file.length();
                
                boolean isImage = lowerName.endsWith(".heic") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
                boolean isMov = lowerName.endsWith(".mov");
                
                if (isImage && movFiles.containsKey(baseName)) {
                    if (!processedLivePhotoBaseNames.contains(baseName)) {
                        processedLivePhotoBaseNames.add(baseName);
                        count++;
                    }
                } else if (isMov && imageFiles.containsKey(baseName)) {
                    if (!processedLivePhotoBaseNames.contains(baseName)) {
                        processedLivePhotoBaseNames.add(baseName);
                        count++;
                    }
                } else {
                    count++;
                }
            } else {
                count++;
            }
        }
        
        return new int[]{count, (int) Math.min(size, Integer.MAX_VALUE)};
    }
    
    private String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    private boolean hasExif(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            for (Directory directory : metadata.getDirectories()) {
                if (directory.getTagCount() > 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    private boolean isImageFile(String extension) {
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    private boolean isVideoFile(String extension) {
        return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    public File getFileById(String rootId, String fileId) {
        MediaRoot root = mediaBrowseConfig.getRootById(rootId);
        if (root == null) {
            logger.warn("根目录不存在: {}", rootId);
            return null;
        }
        
        String relativePath = decodeFileId(fileId);
        logger.debug("解码fileId: {} -> {}", fileId, relativePath);
        
        if (!isPathSafe(relativePath)) {
            logger.warn("不安全的路径: {}", relativePath);
            return null;
        }
        
        File rootDir = new File(root.getPath());
        
        String cleanPath = relativePath.startsWith("/") ? 
                          relativePath.substring(1) : relativePath;
        
        if (cleanPath.isEmpty()) {
            return rootDir;
        }
        
        File targetFile = new File(rootDir, cleanPath);
        
        try {
            String rootPath = rootDir.getCanonicalPath();
            String filePath = targetFile.getCanonicalPath();
            
            if (!filePath.startsWith(rootPath)) {
                logger.warn("路径穿越尝试: {} 不在 {} 内", filePath, rootPath);
                return null;
            }
            
            if (!targetFile.exists()) {
                logger.warn("文件不存在: {}", targetFile.getAbsolutePath());
                return null;
            }
            
            return targetFile;
            
        } catch (Exception e) {
            logger.error("获取文件失败", e);
            return null;
        }
    }
    
    private boolean isPathSafe(String path) {
        return path != null && 
               !path.contains("..") && 
               !path.contains("\\");
    }
}
