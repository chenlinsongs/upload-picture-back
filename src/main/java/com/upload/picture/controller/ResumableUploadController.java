package com.upload.picture.controller;

import com.upload.picture.model.UploadTask;
import com.upload.picture.service.ResumableUploadService;
import com.upload.picture.service.DirectoryCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

/**
 * 断点续传上传控制器
 * 提供分片上传的 REST API
 */
@Controller
@RequestMapping("/image/upload")
public class ResumableUploadController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumableUploadController.class);
    
    @Autowired
    private ResumableUploadService uploadService;
    
    @Autowired(required = false)
    private DirectoryCacheService directoryCacheService;
    
    @GetMapping("/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkExistingUpload(
            @RequestParam("assetId") String assetId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("检查上传任务 - assetId: {}", assetId);
            
            UploadTask task = uploadService.findTaskByAssetId(assetId);
            
            if (task != null) {
                result.put("exists", true);
                result.put("uploadId", task.getUploadId());
                result.put("fileName", task.getFileName());
                result.put("fileSize", task.getFileSize());
                result.put("chunkSize", task.getChunkSize());
                result.put("totalChunks", task.getTotalChunks());
                result.put("uploadedChunks", new ArrayList<>(task.getUploadedChunks()));
                result.put("progress", task.getProgress());
                result.put("createdAt", task.getCreatedAt());
                result.put("expiresAt", task.getExpiresAt());
                
                logger.info("找到未完成的上传: uploadId={}, progress={:.1f}%", 
                           task.getUploadId(), task.getProgress() * 100);
            } else {
                result.put("exists", false);
                logger.info("未找到上传任务: assetId={}", assetId);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("检查上传任务失败", e);
            result.put("exists", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initUpload(
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String assetId = (String) request.get("assetId");
            String fileName = (String) request.get("fileName");
            Number fileSizeNum = (Number) request.get("fileSize");
            String contentType = (String) request.get("contentType");
            String fileType = (String) request.get("fileType");
            String metadataJson = (String) request.get("metadata");
            String folder = (String) request.get("folder");
            
            if (assetId == null || assetId.isEmpty()) {
                result.put("success", false);
                result.put("error", "assetId 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (fileName == null || fileName.isEmpty()) {
                result.put("success", false);
                result.put("error", "fileName 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (fileSizeNum == null || fileSizeNum.longValue() <= 0) {
                result.put("success", false);
                result.put("error", "fileSize 必须大于0");
                return ResponseEntity.badRequest().body(result);
            }
            
            long fileSize = fileSizeNum.longValue();
            
            logger.info("初始化上传 - assetId: {}, fileName: {}, fileSize: {} MB, folder: {}", 
                       assetId, fileName, fileSize / 1024 / 1024, folder);
            
            UploadTask task = uploadService.initUpload(assetId, fileName, fileSize, 
                                                        contentType, fileType, metadataJson, folder);
            
            if ("FILE_EXISTS".equals(task.getUploadId())) {
                logger.info("文件已存在，无需上传: {}", fileName);
                result.put("success", true);
                result.put("fileExists", true);
                result.put("uploadId", null);
                result.put("message", "文件已存在，跳过上传");
                return ResponseEntity.ok(result);
            }
            
            boolean resumed = task.getUploadedChunks().size() > 0;
            
            result.put("success", true);
            result.put("fileExists", false);
            result.put("uploadId", task.getUploadId());
            result.put("chunkSize", task.getChunkSize());
            result.put("totalChunks", task.getTotalChunks());
            result.put("uploadedChunks", new ArrayList<>(task.getUploadedChunks()));
            result.put("resumed", resumed);
            result.put("progress", task.getProgress());
            result.put("expiresAt", task.getExpiresAt());
            
            if (resumed) {
                logger.info("续传任务: uploadId={}, 已上传 {}/{} 分片", 
                           task.getUploadId(), task.getUploadedChunks().size(), task.getTotalChunks());
            } else {
                logger.info("新建任务: uploadId={}, 总分片数: {}", 
                           task.getUploadId(), task.getTotalChunks());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("初始化上传失败", e);
            result.put("success", false);
            result.put("error", "初始化失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/chunk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.debug("接收分片: uploadId={}, chunkIndex={}, size={} KB", 
                        uploadId, chunkIndex, file.getSize() / 1024);
            
            if (uploadId == null || uploadId.isEmpty()) {
                result.put("success", false);
                result.put("error", "uploadId 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("error", "分片数据不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            boolean saved = uploadService.saveChunk(uploadId, chunkIndex, file);
            
            if (saved) {
                UploadTask task = uploadService.getTask(uploadId);
                
                result.put("success", true);
                result.put("chunkIndex", chunkIndex);
                result.put("received", file.getSize());
                
                if (task != null) {
                    result.put("uploadedChunks", task.getUploadedChunks().size());
                    result.put("totalChunks", task.getTotalChunks());
                    result.put("progress", task.getProgress());
                }
                
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("error", "保存分片失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            
        } catch (Exception e) {
            logger.error("上传分片失败: uploadId={}, chunkIndex={}", uploadId, chunkIndex, e);
            result.put("success", false);
            result.put("error", "上传分片失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUploadStatus(
            @RequestParam("uploadId") String uploadId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            UploadTask task = uploadService.getTask(uploadId);
            
            if (task == null) {
                result.put("exists", false);
                result.put("error", "任务不存在或已过期");
                return ResponseEntity.ok(result);
            }
            
            result.put("exists", true);
            result.put("uploadId", task.getUploadId());
            result.put("assetId", task.getAssetId());
            result.put("fileName", task.getFileName());
            result.put("fileSize", task.getFileSize());
            result.put("chunkSize", task.getChunkSize());
            result.put("totalChunks", task.getTotalChunks());
            result.put("uploadedChunks", new ArrayList<>(task.getUploadedChunks()));
            result.put("progress", task.getProgress());
            result.put("complete", task.isComplete());
            result.put("createdAt", task.getCreatedAt());
            result.put("expiresAt", task.getExpiresAt());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("查询上传状态失败: uploadId={}", uploadId, e);
            result.put("exists", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeUpload(
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String uploadId = (String) request.get("uploadId");
            Number timestampNum = (Number) request.get("timestamp");
            Long timestamp = timestampNum != null ? timestampNum.longValue() : null;
            String folder = (String) request.get("folder");
            
            if (uploadId == null || uploadId.isEmpty()) {
                result.put("success", false);
                result.put("error", "uploadId 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            logger.info("完成上传请求: uploadId={}, folder={}", uploadId, folder);
            
            File targetFile = uploadService.completeUpload(uploadId, timestamp, folder);
            
            if (targetFile != null) {
                if (directoryCacheService != null) {
                    directoryCacheService.clear();
                    logger.info("断点续传完成，已清除浏览缓存 (folder={})", folder);
                }
                
                result.put("success", true);
                result.put("message", "上传完成");
                result.put("filePath", targetFile.getAbsolutePath());
                result.put("fileName", targetFile.getName());
                result.put("fileSize", targetFile.length());
                
                logger.info("上传完成: {} ({} MB)", 
                           targetFile.getName(), targetFile.length() / 1024 / 1024);
                
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("error", "合并分片失败，可能分片未上传完成");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            
        } catch (Exception e) {
            logger.error("完成上传失败", e);
            result.put("success", false);
            result.put("error", "完成上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelUpload(
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String uploadId = (String) request.get("uploadId");
            
            if (uploadId == null || uploadId.isEmpty()) {
                result.put("success", false);
                result.put("error", "uploadId 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            boolean cancelled = uploadService.cancelUpload(uploadId);
            
            result.put("success", cancelled);
            result.put("message", cancelled ? "已取消上传" : "任务不存在");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("取消上传失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = uploadService.getStatistics();
        stats.put("activeTasks", uploadService.getAllActiveTasks().size());
        return ResponseEntity.ok(stats);
    }
}
