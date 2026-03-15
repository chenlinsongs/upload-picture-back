package com.upload.picture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upload.picture.model.MediaRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class RootConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(RootConfigService.class);
    private static final String CONFIG_FILE_NAME = "media_roots_config.json";
    
    private final ObjectMapper objectMapper;
    private final String configFilePath;
    
    private List<MediaRoot> cachedRoots = null;
    private long lastLoadTime = 0;
    
    public RootConfigService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        String userHome = System.getProperty("user.home");
        String appConfigDir = userHome + "/FileUploadManager/config/";
        this.configFilePath = appConfigDir + CONFIG_FILE_NAME;
        
        new File(appConfigDir).mkdirs();
        
        loadRootsFromFile();
        
        logger.info("根目录配置服务初始化完成，配置文件路径: {}, 已加载 {} 个根目录", 
                    configFilePath, cachedRoots != null ? cachedRoots.size() : 0);
    }
    
    public List<MediaRoot> getRoots() {
        if (cachedRoots == null) {
            loadRootsFromFile();
        }
        return cachedRoots != null ? new ArrayList<>(cachedRoots) : new ArrayList<>();
    }
    
    private void loadRootsFromFile() {
        File configFile = new File(configFilePath);
        
        if (!configFile.exists()) {
            logger.debug("配置文件不存在，返回空列表: {}", configFilePath);
            cachedRoots = new ArrayList<>();
            return;
        }
        
        try {
            MediaRootConfig config = objectMapper.readValue(configFile, MediaRootConfig.class);
            cachedRoots = config.getRoots();
            lastLoadTime = System.currentTimeMillis();
            logger.debug("从文件加载 {} 个根目录配置", cachedRoots.size());
        } catch (IOException e) {
            logger.error("加载根目录配置失败", e);
            cachedRoots = new ArrayList<>();
        }
    }
    
    public void reloadRoots() {
        logger.info("手动重新加载根目录配置");
        loadRootsFromFile();
    }
    
    public boolean saveRoots(List<MediaRoot> roots) {
        try {
            MediaRootConfig config = new MediaRootConfig();
            config.setRoots(roots);
            
            objectMapper.writeValue(new File(configFilePath), config);
            
            cachedRoots = new ArrayList<>(roots);
            lastLoadTime = System.currentTimeMillis();
            
            logger.info("成功保存 {} 个根目录配置", roots.size());
            return true;
        } catch (IOException e) {
            logger.error("保存根目录配置失败", e);
            return false;
        }
    }
    
    public MediaRoot addRoot(String name, String path, String icon, String description, boolean readonly) {
        List<MediaRoot> roots = getRoots();
        
        String id = generateStableId(path);
        
        for (MediaRoot existing : roots) {
            if (existing.getId().equals(id)) {
                logger.warn("根目录已存在，跳过添加: path={}, id={}", path, id);
                return existing;
            }
        }
        
        MediaRoot newRoot = new MediaRoot(id, name, path, icon, description, readonly);
        roots.add(newRoot);
        
        if (saveRoots(roots)) {
            logger.info("成功添加根目录: {}", newRoot);
            return newRoot;
        }
        
        return null;
    }
    
    private String generateStableId(String path) {
        try {
            String normalizedPath = path.trim();
            if (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(normalizedPath.getBytes("UTF-8"));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16);
            
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            logger.error("生成稳定ID失败，回退到时间戳", e);
            return String.valueOf(System.currentTimeMillis()).substring(0, 16);
        }
    }
    
    public boolean updateRoot(MediaRoot updatedRoot) {
        List<MediaRoot> roots = getRoots();
        
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).getId().equals(updatedRoot.getId())) {
                roots.set(i, updatedRoot);
                boolean saved = saveRoots(roots);
                if (saved) {
                    logger.info("成功更新根目录: {}", updatedRoot);
                }
                return saved;
            }
        }
        
        logger.warn("未找到要更新的根目录: {}", updatedRoot.getId());
        return false;
    }
    
    public boolean deleteRoot(String rootId) {
        List<MediaRoot> roots = getRoots();
        
        boolean removed = roots.removeIf(root -> root.getId().equals(rootId));
        
        if (removed) {
            boolean saved = saveRoots(roots);
            if (saved) {
                logger.info("成功删除根目录: {}", rootId);
            }
            return saved;
        }
        
        logger.warn("未找到要删除的根目录: {}", rootId);
        return false;
    }
    
    public MediaRoot getRootById(String rootId) {
        List<MediaRoot> roots = getRoots();
        return roots.stream()
                .filter(root -> root.getId().equals(rootId))
                .findFirst()
                .orElse(null);
    }
    
    public boolean isPathAllowed(String rootId, String relativePath) {
        MediaRoot root = getRootById(rootId);
        if (root == null) {
            logger.warn("根目录不存在: {}", rootId);
            return false;
        }
        
        try {
            Path rootPath = Paths.get(root.getPath()).normalize();
            
            String cleanPath = relativePath;
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            
            if (cleanPath.isEmpty()) {
                return true;
            }
            
            Path requestedPath = rootPath.resolve(cleanPath).normalize();
            boolean allowed = requestedPath.startsWith(rootPath);
            
            if (!allowed) {
                logger.warn("路径穿越尝试被阻止 - 根目录: {}, 请求路径: {}", rootPath, requestedPath);
            }
            
            return allowed;
        } catch (Exception e) {
            logger.error("路径验证失败", e);
            return false;
        }
    }
    
    public String getFullPath(String rootId, String relativePath) {
        MediaRoot root = getRootById(rootId);
        if (root == null) {
            return null;
        }
        
        if (!isPathAllowed(rootId, relativePath)) {
            return null;
        }
        
        try {
            Path rootPath = Paths.get(root.getPath()).normalize();
            
            String cleanPath = relativePath;
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            
            if (cleanPath.isEmpty()) {
                return rootPath.toString();
            }
            
            Path fullPath = rootPath.resolve(cleanPath).normalize();
            return fullPath.toString();
        } catch (Exception e) {
            logger.error("获取完整路径失败", e);
            return null;
        }
    }
    
    private static class MediaRootConfig {
        private List<MediaRoot> roots = new ArrayList<>();
        
        public List<MediaRoot> getRoots() { return roots; }
        public void setRoots(List<MediaRoot> roots) { this.roots = roots; }
    }
}
