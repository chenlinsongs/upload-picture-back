package com.upload.picture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upload.picture.model.UploadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 断点续传上传服务
 * 负责管理分片上传任务、分片存储、文件合并等
 */
@Service
public class ResumableUploadService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumableUploadService.class);
    
    public static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024L;
    public static final long TASK_EXPIRE_DURATION = 24 * 60 * 60 * 1000L;
    
    private final Map<String, UploadTask> uploadTasks = new ConcurrentHashMap<>();
    private final Map<String, String> assetIdToUploadId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private String getTempUploadDir() {
        String userHome = System.getProperty("user.home");
        String defaultTempDir = userHome + "/FileUploadManager/uploads-temp/";
        return System.getProperty("app.upload.temp.dir", defaultTempDir);
    }
    
    private String getUploadBaseDir() {
        String userHome = System.getProperty("user.home");
        String defaultUploadBase = userHome + "/FileUploadManager/uploads/";
        return System.getProperty("app.upload.base.dir", defaultUploadBase);
    }
    
    private String getVideoDir() {
        return System.getProperty("app.upload.video.dir", getUploadBaseDir() + "videos/");
    }
    
    @PostConstruct
    public void init() {
        logger.info("初始化断点续传服务...");
        recoverTasksFromDisk();
        logger.info("断点续传服务初始化完成，当前任务数: {}", uploadTasks.size());
    }
    
    private void recoverTasksFromDisk() {
        File tempDir = new File(getTempUploadDir());
        if (!tempDir.exists()) {
            return;
        }
        
        File[] taskDirs = tempDir.listFiles(File::isDirectory);
        if (taskDirs == null) {
            return;
        }
        
        for (File taskDir : taskDirs) {
            try {
                File manifestFile = new File(taskDir, "manifest.json");
                if (manifestFile.exists()) {
                    UploadTask task = objectMapper.readValue(manifestFile, UploadTask.class);
                    
                    if (task.isExpired()) {
                        logger.info("删除过期任务: {}", task.getUploadId());
                        deleteTaskDirectory(taskDir);
                    } else {
                        uploadTasks.put(task.getUploadId(), task);
                        if (task.getAssetId() != null) {
                            assetIdToUploadId.put(task.getAssetId(), task.getUploadId());
                        }
                        logger.info("恢复上传任务: {}", task);
                    }
                }
            } catch (Exception e) {
                logger.warn("恢复任务失败: {}, 错误: {}", taskDir.getName(), e.getMessage());
            }
        }
    }
    
    public UploadTask findTaskByAssetId(String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        
        String uploadId = assetIdToUploadId.get(assetId);
        if (uploadId == null) {
            return null;
        }
        
        UploadTask task = uploadTasks.get(uploadId);
        if (task == null || task.isExpired()) {
            assetIdToUploadId.remove(assetId);
            if (task != null) {
                uploadTasks.remove(uploadId);
            }
            return null;
        }
        
        return task;
    }
    
    public UploadTask getTask(String uploadId) {
        UploadTask task = uploadTasks.get(uploadId);
        if (task != null && task.isExpired()) {
            cleanupTask(uploadId);
            return null;
        }
        return task;
    }
    
    public UploadTask initUpload(String assetId, String fileName, long fileSize, 
                                  String contentType, String fileType, String metadataJson) {
        return initUpload(assetId, fileName, fileSize, contentType, fileType, metadataJson, null);
    }
    
    public UploadTask initUpload(String assetId, String fileName, long fileSize, 
                                  String contentType, String fileType, String metadataJson,
                                  String folder) {
        
        String targetDirPath;
        if (folder != null && !folder.isEmpty()) {
            targetDirPath = getUploadBaseDir() + folder + "/";
        } else {
            targetDirPath = getVideoDir();
        }
        File targetFile = new File(targetDirPath + fileName);
        if (targetFile.exists()) {
            logger.info("文件已存在，跳过上传初始化: {}", targetFile.getAbsolutePath());
            UploadTask existingFileTask = new UploadTask("FILE_EXISTS", assetId, fileName, fileSize, 
                                                         contentType, DEFAULT_CHUNK_SIZE, 0);
            existingFileTask.setFileType(fileType != null ? fileType : "video");
            existingFileTask.setTargetFolder(folder);
            existingFileTask.setStatus("FILE_EXISTS");
            return existingFileTask;
        }
        
        UploadTask existingTask = findTaskByAssetId(assetId);
        if (existingTask != null) {
            logger.info("发现未完成的上传任务: {}", existingTask);
            return existingTask;
        }
        
        String uploadId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil((double) fileSize / DEFAULT_CHUNK_SIZE);
        
        UploadTask task = new UploadTask(uploadId, assetId, fileName, fileSize, 
                                         contentType, DEFAULT_CHUNK_SIZE, totalChunks);
        task.setFileType(fileType != null ? fileType : "video");
        task.setMetadataJson(metadataJson);
        task.setTargetFolder(folder);
        
        File taskDir = new File(getTempUploadDir() + uploadId);
        if (!taskDir.exists()) {
            taskDir.mkdirs();
        }
        
        uploadTasks.put(uploadId, task);
        if (assetId != null && !assetId.isEmpty()) {
            assetIdToUploadId.put(assetId, uploadId);
        }
        
        saveManifest(task);
        
        logger.info("创建上传任务: uploadId={}, fileName={}, fileSize={}, totalChunks={}, folder={}", 
                   uploadId, fileName, fileSize, totalChunks, folder);
        
        return task;
    }
    
    public boolean saveChunk(String uploadId, int chunkIndex, MultipartFile chunkFile) {
        UploadTask task = getTask(uploadId);
        if (task == null) {
            logger.warn("上传任务不存在或已过期: {}", uploadId);
            return false;
        }
        
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            logger.warn("无效的分片索引: {} (总分片数: {})", chunkIndex, task.getTotalChunks());
            return false;
        }
        
        if (task.getUploadedChunks().contains(chunkIndex)) {
            logger.info("分片已存在，跳过: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
            return true;
        }
        
        try {
            File chunkPath = new File(getTempUploadDir() + uploadId + "/chunk_" + chunkIndex + ".tmp");
            chunkFile.transferTo(chunkPath);
            
            task.addUploadedChunk(chunkIndex);
            saveManifest(task);
            
            logger.info("保存分片成功: uploadId={}, chunkIndex={}/{}, progress={:.1f}%", 
                       uploadId, chunkIndex, task.getTotalChunks(), task.getProgress() * 100);
            
            return true;
            
        } catch (IOException e) {
            logger.error("保存分片失败: uploadId={}, chunkIndex={}, error={}", 
                        uploadId, chunkIndex, e.getMessage());
            return false;
        }
    }
    
    public File completeUpload(String uploadId, Long timestamp, String folder) {
        UploadTask task = getTask(uploadId);
        if (task == null) {
            logger.warn("上传任务不存在或已过期: {}", uploadId);
            return null;
        }
        
        if (!task.isComplete()) {
            logger.warn("分片未完成: uploadId={}, uploaded={}/{}", 
                       uploadId, task.getUploadedChunks().size(), task.getTotalChunks());
            return null;
        }
        
        try {
            String targetDirPath;
            if (folder != null && !folder.trim().isEmpty()) {
                String safeFolder = folder.trim()
                    .replaceAll("[/\\\\:*?\"<>|]", "_")
                    .replaceAll("\\.\\.", "");
                targetDirPath = getUploadBaseDir() + safeFolder + "/";
                logger.info("使用指定文件夹: {}", targetDirPath);
            } else {
                targetDirPath = getVideoDir();
            }
            
            File targetDir = new File(targetDirPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            File targetFile = new File(targetDirPath + task.getFileName());
            
            if (targetFile.exists()) {
                logger.info("文件已存在，跳过合并: {}", targetFile.getAbsolutePath());
                cleanupTask(uploadId);
                return targetFile;
            }
            
            logger.info("开始合并分片: uploadId={}, totalChunks={}", uploadId, task.getTotalChunks());
            mergeChunks(task, targetFile);
            
            if (timestamp != null && timestamp > 0) {
                setFileTimestamp(targetFile, timestamp);
            }
            
            cleanupTask(uploadId);
            
            logger.info("上传完成: {}, 大小: {} MB", 
                       targetFile.getAbsolutePath(), targetFile.length() / 1024 / 1024);
            
            return targetFile;
            
        } catch (Exception e) {
            logger.error("完成上传失败: uploadId={}, error={}", uploadId, e.getMessage(), e);
            return null;
        }
    }
    
    private void mergeChunks(UploadTask task, File targetFile) throws IOException {
        File taskDir = new File(getTempUploadDir() + task.getUploadId());
        
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 8192)) {
            
            byte[] buffer = new byte[8192];
            
            for (int i = 0; i < task.getTotalChunks(); i++) {
                File chunkFile = new File(taskDir, "chunk_" + i + ".tmp");
                
                if (!chunkFile.exists()) {
                    throw new IOException("分片文件不存在: " + chunkFile.getName());
                }
                
                try (FileInputStream fis = new FileInputStream(chunkFile);
                     BufferedInputStream bis = new BufferedInputStream(fis, 8192)) {
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                }
            }
            
            bos.flush();
        }
        
        logger.info("分片合并完成: {} -> {}", task.getUploadId(), targetFile.getName());
    }
    
    private void setFileTimestamp(File file, long timestamp) {
        try {
            Path path = file.toPath();
            FileTime fileTime = FileTime.fromMillis(timestamp);
            
            Files.setLastModifiedTime(path, fileTime);
            
            try {
                BasicFileAttributeView attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                if (attributes != null) {
                    attributes.setTimes(fileTime, fileTime, fileTime);
                }
            } catch (Exception e) {
                logger.debug("无法设置文件创建时间: {}", e.getMessage());
            }
            
        } catch (IOException e) {
            logger.warn("设置文件时间戳失败: {}", e.getMessage());
        }
    }
    
    public boolean cancelUpload(String uploadId) {
        UploadTask task = uploadTasks.get(uploadId);
        if (task == null) {
            return false;
        }
        
        cleanupTask(uploadId);
        logger.info("取消上传任务: {}", uploadId);
        return true;
    }
    
    private void cleanupTask(String uploadId) {
        UploadTask task = uploadTasks.remove(uploadId);
        if (task != null && task.getAssetId() != null) {
            assetIdToUploadId.remove(task.getAssetId());
        }
        
        File taskDir = new File(getTempUploadDir() + uploadId);
        if (taskDir.exists()) {
            deleteTaskDirectory(taskDir);
        }
    }
    
    private void saveManifest(UploadTask task) {
        try {
            File manifestFile = new File(getTempUploadDir() + task.getUploadId() + "/manifest.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, task);
        } catch (IOException e) {
            logger.error("保存 manifest 失败: {}", e.getMessage());
        }
    }
    
    private void deleteTaskDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteTaskDirectory(file);
                }
            }
        }
        dir.delete();
    }
    
    @Scheduled(cron = "0 0 * * * *")
    public void cleanExpiredTasks() {
        logger.info("开始清理过期上传任务...");
        
        List<String> expiredIds = uploadTasks.entrySet().stream()
                .filter(e -> e.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        for (String uploadId : expiredIds) {
            cleanupTask(uploadId);
            logger.info("清理过期任务: {}", uploadId);
        }
        
        logger.info("清理完成，删除 {} 个过期任务，剩余 {} 个任务", 
                   expiredIds.size(), uploadTasks.size());
    }
    
    public List<UploadTask> getAllActiveTasks() {
        return new ArrayList<>(uploadTasks.values());
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasks", uploadTasks.size());
        stats.put("tempDirectory", getTempUploadDir());
        stats.put("videoDirectory", getVideoDir());
        stats.put("defaultChunkSize", DEFAULT_CHUNK_SIZE);
        stats.put("taskExpireDuration", TASK_EXPIRE_DURATION);
        return stats;
    }
}
