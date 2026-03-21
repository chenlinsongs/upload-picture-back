package com.upload.picture.controller;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upload.picture.model.ImageMetadata;
import com.upload.picture.model.LivePhotoMetadata;
import com.upload.picture.model.VideoMetadata;
import com.upload.picture.model.MediaRoot;
import com.upload.picture.config.MediaBrowseConfig;
import com.upload.picture.service.FileBrowseService;
import com.upload.picture.service.ThumbnailService;
import com.upload.picture.service.DirectoryCacheService;
import com.upload.picture.config.StorageConfig;
import com.upload.picture.util.TimestampParser;
import com.upload.picture.util.VideoMetadataWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Enumeration;

@Controller
@RequestMapping("/image")
public class ImageUploadController {

    private static final Logger logger = LoggerFactory.getLogger(ImageUploadController.class);
    
    @Autowired(required = false)
    private MediaBrowseConfig mediaBrowseConfig;
    
    @Autowired(required = false)
    private FileBrowseService fileBrowseService;
    
    @Autowired(required = false)
    private ThumbnailService thumbnailService;
    
    @Autowired(required = false)
    private DirectoryCacheService directoryCacheService;
    
    @Autowired
    private StorageConfig storageConfig;
    
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList("mov", "mp4", "avi", "mkv");
    
    private static final List<String> ALLOWED_IMAGE_CONTENT_TYPES = Arrays.asList(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", 
        "image/webp", "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence"
    );
    
    private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES = Arrays.asList(
        "video/quicktime", "video/mp4", "video/x-msvideo", "video/x-matroska"
    );
    

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam(value = "timestamp", required = false) Long timestamp,
            @RequestParam(value = "folder", required = false) String folder) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "上传的文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            logger.info("接收到上传文件: {}, 大小: {} bytes, Content-Type: {}, 目标文件夹: {}", 
                       originalFilename, file.getSize(), contentType, folder);

            String fileExtension = getFileExtension(originalFilename);
            if (!isValidImageExtension(fileExtension)) {
                if (!isValidImageContentType(contentType)) {
                    result.put("success", false);
                    result.put("message", "不支持的图片格式");
                    return ResponseEntity.badRequest().body(result);
                }
                
                String extensionFromContentType = getExtensionFromContentType(contentType);
                if (!extensionFromContentType.isEmpty()) {
                    originalFilename = originalFilename + "." + extensionFromContentType;
                }
            }

            String uploadDirPath = storageConfig.getTargetUploadDir(folder);
            File uploadDir = new File(uploadDirPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    result.put("success", false);
                    result.put("message", "创建上传目录失败，请检查应用权限");
                    result.put("path", uploadDir.getAbsolutePath());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }

            String filePath = uploadDirPath + originalFilename;
            File targetFile = new File(filePath);
            
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (targetFile.exists()) {
                result.put("success", true);
                result.put("message", "文件已存在，无需重复上传");
                result.put("originalFilename", originalFilename);
                result.put("savedFilename", originalFilename);
                result.put("filePath", filePath);
                result.put("fileSize", file.getSize());
                result.put("contentType", file.getContentType());
                result.put("existed", true);
                return ResponseEntity.ok(result);
            }

            ImageMetadata imageMetadata = null;
            if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    imageMetadata = objectMapper.readValue(metadataJson, ImageMetadata.class);
                } catch (Exception e) {
                    logger.warn("无法解析元数据JSON: {}", e.getMessage());
                }
            }
            
            file.transferTo(targetFile);
            logger.info("文件保存成功: {}", filePath);
            
            Long finalTimestamp = null;
            String timestampSource = null;
            
            Long fileMetadataTimestamp = extractTimestampFromMetadata(targetFile, originalFilename);
            if (fileMetadataTimestamp != null && fileMetadataTimestamp > 0) {
                finalTimestamp = fileMetadataTimestamp;
                timestampSource = "file_metadata";
            }
            
            if (finalTimestamp == null && imageMetadata != null && imageMetadata.getDateTimeOriginal() != null) {
                Long parsedTimestamp = TimestampParser.parseTimestamp(imageMetadata.getDateTimeOriginal());
                if (parsedTimestamp != null) {
                    finalTimestamp = parsedTimestamp;
                    timestampSource = "metadata_dateTimeOriginal";
                }
            }
            
            if (finalTimestamp == null && imageMetadata != null && imageMetadata.getCreationDate() != null) {
                Long parsedTimestamp = TimestampParser.parseTimestamp(imageMetadata.getCreationDate());
                if (parsedTimestamp != null) {
                    finalTimestamp = parsedTimestamp;
                    timestampSource = "metadata_creationDate";
                }
            }
            
            if (finalTimestamp == null && timestamp != null && timestamp > 0) {
                finalTimestamp = timestamp;
                timestampSource = "client_param";
            }
            
            if (finalTimestamp != null) {
                setFileTimestamp(targetFile, finalTimestamp);
                logger.info("已设置文件时间戳: {} -> {} (来源: {})", 
                    originalFilename, new Date(finalTimestamp), timestampSource);
            }

            invalidateBrowseCache(folder);
            
            result.put("success", true);
            result.put("message", "图片上传成功");
            result.put("originalFilename", originalFilename);
            result.put("savedFilename", originalFilename);
            result.put("filePath", filePath);
            result.put("fileSize", file.getSize());
            result.put("contentType", file.getContentType());
            result.put("existed", false);
            if (finalTimestamp != null) {
                result.put("timestamp", finalTimestamp);
                result.put("timestampDate", new Date(finalTimestamp).toString());
                result.put("timestampSource", timestampSource);
            }

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            logger.error("文件保存失败", e);
            result.put("success", false);
            result.put("message", "文件保存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } catch (Exception e) {
            logger.error("上传过程发生异常", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/batch-upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> batchUploadImages(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();

        if (files == null || files.length == 0) {
            result.put("success", false);
            result.put("message", "未接收到任何文件");
            return ResponseEntity.badRequest().body(result);
        }

        logger.info("接收到批量上传请求，文件数量: {}", files.length);

        List<Map<String, Object>> successList = new ArrayList<>();
        List<Map<String, Object>> failList = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("originalFilename", file.getOriginalFilename());

            try {
                if (file.isEmpty()) {
                    fileResult.put("success", false);
                    fileResult.put("message", "文件为空");
                    failList.add(fileResult);
                    continue;
                }

                String originalFilename = file.getOriginalFilename();
                String fileExtension = getFileExtension(originalFilename);

                if (!isValidImageExtension(fileExtension)) {
                    fileResult.put("success", false);
                    fileResult.put("message", "不支持的图片格式");
                    failList.add(fileResult);
                    continue;
                }

                String newFilename = generateFilename(fileExtension);
                String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
                String uploadPath = storageConfig.getImageDir() + datePath + "/";

                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }

                String filePath = uploadPath + newFilename;
                File targetFile = new File(filePath);
                file.transferTo(targetFile);

                fileResult.put("success", true);
                fileResult.put("savedFilename", newFilename);
                fileResult.put("filePath", filePath);
                fileResult.put("fileSize", file.getSize());
                successList.add(fileResult);

            } catch (Exception e) {
                logger.error("批量上传-文件保存失败: " + file.getOriginalFilename(), e);
                fileResult.put("success", false);
                fileResult.put("message", "保存失败: " + e.getMessage());
                failList.add(fileResult);
            }
        }

        result.put("total", files.length);
        result.put("successCount", successList.size());
        result.put("failCount", failList.size());
        result.put("successList", successList);
        result.put("failList", failList);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/check-exists")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkFileExists(@org.springframework.web.bind.annotation.RequestBody Map<String, String> requestBody) {
        Map<String, Object> result = new HashMap<>();

        try {
            String fileName = requestBody.get("fileName");
            String fileType = requestBody.get("fileType");
            String folder = requestBody.get("folder");
            
            logger.info("收到文件存在性检查请求 - 文件名: {}, 文件类型: {}, 文件夹: {}", fileName, fileType, folder);

            if (fileName == null || fileName.isEmpty()) {
                result.put("exists", false);
                result.put("message", "文件名不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (fileType == null || fileType.isEmpty()) {
                result.put("exists", false);
                result.put("message", "文件类型不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            final String fileNamePrefix;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileNamePrefix = fileName.substring(0, dotIndex);
            } else {
                fileNamePrefix = fileName;
            }

            if (folder != null && !folder.isEmpty()) {
                String customDirPath = storageConfig.getUploadBaseDir() + folder + "/";
                File customDir = new File(customDirPath);
                
                if (customDir.exists() && customDir.isDirectory()) {
                    File[] matchedFiles = customDir.listFiles((dir, name) -> {
                        String namePrefix = name;
                        int idx = name.lastIndexOf('.');
                        if (idx > 0) {
                            namePrefix = name.substring(0, idx);
                        }
                        return namePrefix.equals(fileNamePrefix);
                    });
                    
                    if (matchedFiles != null && matchedFiles.length > 0) {
                        result.put("exists", true);
                        result.put("matchedFileName", matchedFiles[0].getName());
                        return ResponseEntity.ok(result);
                    }
                }
                
                result.put("exists", false);
                result.put("message", "文件不存在");
                return ResponseEntity.ok(result);
            }

            boolean exists = false;
            String matchedFileName = "";
            
            switch (fileType.toLowerCase()) {
                case "image":
                    File imageDir = new File(storageConfig.getImageDir());
                    if (imageDir.exists() && imageDir.isDirectory()) {
                        File[] imageFiles = imageDir.listFiles((dir, name) -> {
                            String namePrefix = name;
                            int idx = name.lastIndexOf('.');
                            if (idx > 0) {
                                namePrefix = name.substring(0, idx);
                            }
                            return namePrefix.equals(fileNamePrefix);
                        });
                        
                        if (imageFiles != null && imageFiles.length > 0) {
                            exists = true;
                            matchedFileName = imageFiles[0].getName();
                        }
                    }
                    break;
                    
                case "video":
                    File videoDir = new File(storageConfig.getVideoDir());
                    if (videoDir.exists() && videoDir.isDirectory()) {
                        File[] videoFiles = videoDir.listFiles((dir, name) -> {
                            String namePrefix = name;
                            int idx = name.lastIndexOf('.');
                            if (idx > 0) {
                                namePrefix = name.substring(0, idx);
                            }
                            return namePrefix.equals(fileNamePrefix);
                        });
                        
                        if (videoFiles != null && videoFiles.length > 0) {
                            exists = true;
                            matchedFileName = videoFiles[0].getName();
                        }
                    }
                    break;
                    
                case "livephoto":
                    File livePhotoDir = new File(storageConfig.getLivePhotoDir());
                    if (livePhotoDir.exists() && livePhotoDir.isDirectory()) {
                        File[] livePhotoImageFiles = livePhotoDir.listFiles((dir, name) -> {
                            String namePrefix = name;
                            int idx = name.lastIndexOf('.');
                            if (idx > 0) {
                                namePrefix = name.substring(0, idx);
                            }
                            String ext = getFileExtension(name).toLowerCase();
                            return namePrefix.equals(fileNamePrefix) && isValidImageExtension(ext);
                        });
                        
                        File[] livePhotoVideoFiles = livePhotoDir.listFiles((dir, name) -> {
                            String namePrefix = name;
                            int idx = name.lastIndexOf('.');
                            if (idx > 0) {
                                namePrefix = name.substring(0, idx);
                            }
                            String ext = getFileExtension(name).toLowerCase();
                            return namePrefix.equals(fileNamePrefix) && isValidVideoExtension(ext);
                        });
                        
                        boolean imageExists = livePhotoImageFiles != null && livePhotoImageFiles.length > 0;
                        boolean videoExists = livePhotoVideoFiles != null && livePhotoVideoFiles.length > 0;
                        
                        exists = imageExists && videoExists;
                        
                        if (exists) {
                            matchedFileName = livePhotoImageFiles[0].getName() + " + " + livePhotoVideoFiles[0].getName();
                        }
                    }
                    break;
                    
                default:
                    result.put("exists", false);
                    result.put("message", "不支持的文件类型: " + fileType);
                    return ResponseEntity.badRequest().body(result);
            }

            result.put("exists", exists);
            result.put("fileName", fileName);
            result.put("fileType", fileType);
            
            if (exists) {
                result.put("message", "文件已存在");
                result.put("matchedFileName", matchedFileName);
            } else {
                result.put("message", "文件不存在");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("检查文件存在性时发生异常", e);
            result.put("exists", false);
            result.put("message", "检查失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/upload-video")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam(value = "timestamp", required = false) Long timestamp,
            @RequestParam(value = "folder", required = false) String folder) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "上传的视频文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            logger.info("接收到视频上传: {}, 大小: {} bytes, Content-Type: {}, 目标文件夹: {}", 
                       originalFilename, file.getSize(), contentType, folder);
            
            VideoMetadata videoMetadata = null;
            if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    videoMetadata = objectMapper.readValue(metadataJson, VideoMetadata.class);
                    logger.info("接收到视频元数据:");
                    logger.info(VideoMetadataWriter.formatMetadataInfo(videoMetadata));
                } catch (Exception e) {
                    logger.warn("无法解析元数据JSON: {}", e.getMessage());
                }
            }

            String fileExtension = getFileExtension(originalFilename);
            if (!isValidVideoExtension(fileExtension)) {
                if (!isValidVideoContentType(contentType)) {
                    result.put("success", false);
                    result.put("message", "不支持的视频格式");
                    return ResponseEntity.badRequest().body(result);
                }
                
                String extensionFromContentType = getExtensionFromContentType(contentType);
                if (!extensionFromContentType.isEmpty()) {
                    originalFilename = originalFilename + "." + extensionFromContentType;
                    fileExtension = extensionFromContentType;
                }
            }

            String videoDirPath = storageConfig.getTargetUploadDir(folder);
            File uploadDir = new File(videoDirPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    result.put("success", false);
                    result.put("message", "创建上传目录失败，请检查应用权限");
                    result.put("path", uploadDir.getAbsolutePath());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }

            String filePath = videoDirPath + originalFilename;
            File targetFile = new File(filePath);
            
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (targetFile.exists()) {
                result.put("success", true);
                result.put("message", "视频文件已存在，无需重复上传");
                result.put("originalFilename", originalFilename);
                result.put("savedFilename", originalFilename);
                result.put("filePath", filePath);
                result.put("fileSize", file.getSize());
                result.put("contentType", file.getContentType());
                result.put("fileType", "video");
                result.put("extension", fileExtension);
                result.put("existed", true);
                return ResponseEntity.ok(result);
            }

            file.transferTo(targetFile);
            logger.info("视频文件保存成功: {}", filePath);
            
            Long finalTimestamp = null;
            String timestampSource = null;
            
            if (videoMetadata != null && videoMetadata.getCreationDate() != null) {
                finalTimestamp = VideoMetadataWriter.parseCreationDate(videoMetadata.getCreationDate());
                if (finalTimestamp != null) {
                    timestampSource = "ios_metadata";
                }
            }
            
            if (finalTimestamp == null) {
                Long fileMetadataTimestamp = extractTimestampFromMetadata(targetFile, originalFilename);
                if (fileMetadataTimestamp != null && fileMetadataTimestamp > 0) {
                    finalTimestamp = fileMetadataTimestamp;
                    timestampSource = "file_metadata";
                }
            }
            
            if (finalTimestamp == null && timestamp != null && timestamp > 0) {
                finalTimestamp = timestamp;
                timestampSource = "client_param";
            }
            
            if (finalTimestamp != null) {
                setFileTimestamp(targetFile, finalTimestamp);
                logger.info("已设置视频文件时间戳: {} -> {}", originalFilename, new Date(finalTimestamp));
            }

            invalidateBrowseCache(folder);
            
            result.put("success", true);
            result.put("message", "视频上传成功");
            result.put("originalFilename", originalFilename);
            result.put("savedFilename", originalFilename);
            result.put("filePath", filePath);
            result.put("fileSize", file.getSize());
            result.put("contentType", file.getContentType());
            result.put("fileType", "video");
            result.put("extension", fileExtension);
            result.put("existed", false);
            if (finalTimestamp != null) {
                result.put("timestamp", finalTimestamp);
                result.put("timestampDate", new Date(finalTimestamp).toString());
                result.put("timestampSource", timestampSource);
            }
            if (videoMetadata != null) {
                result.put("hasMetadata", true);
                result.put("metadataInfo", createMetadataInfo(videoMetadata));
            }

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            logger.error("视频文件保存失败", e);
            result.put("success", false);
            result.put("message", "视频文件保存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } catch (Exception e) {
            logger.error("视频上传过程发生异常", e);
            result.put("success", false);
            result.put("message", "视频上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/upload-livephoto")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadLivePhoto(
            @RequestParam(value = "image", required = false) MultipartFile imageFile,
            @RequestParam(value = "video", required = false) MultipartFile videoFile,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "imageTimestamp", required = false) Long imageTimestamp,
            @RequestParam(value = "videoTimestamp", required = false) Long videoTimestamp,
            @RequestParam(value = "folder", required = false) String folder) {
        
        Map<String, Object> result = new HashMap<>();

        try {
            if (imageFile == null || imageFile.isEmpty()) {
                result.put("success", false);
                result.put("message", "Live Photo 图片文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (videoFile == null || videoFile.isEmpty()) {
                result.put("success", false);
                result.put("message", "Live Photo 视频文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            String imageOriginalFilename = imageFile.getOriginalFilename();
            String videoOriginalFilename = videoFile.getOriginalFilename();
            String imageContentType = imageFile.getContentType();
            String videoContentType = videoFile.getContentType();
            
            if (imageOriginalFilename == null || imageOriginalFilename.isEmpty()) {
                result.put("success", false);
                result.put("message", "Live Photo 图片文件名不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (videoOriginalFilename == null || videoOriginalFilename.isEmpty()) {
                result.put("success", false);
                result.put("message", "Live Photo 视频文件名不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            logger.info("接收到Live Photo上传 - 图片: {}, 视频: {}, 目标文件夹: {}", 
                       imageOriginalFilename, videoOriginalFilename, folder);
            
            LivePhotoMetadata livePhotoMetadata = null;
            ImageMetadata imageMetadata = null;
            VideoMetadata videoMetadata = null;
            
            if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    livePhotoMetadata = objectMapper.readValue(metadataJson, LivePhotoMetadata.class);
                    imageMetadata = livePhotoMetadata.getImageMetadata();
                    videoMetadata = livePhotoMetadata.getVideoMetadata();
                } catch (Exception e) {
                    logger.warn("无法解析Live Photo元数据JSON: {}", e.getMessage());
                }
            }

            String imageExtension = getFileExtension(imageOriginalFilename);
            if (!isValidImageExtension(imageExtension)) {
                if (!isValidImageContentType(imageContentType)) {
                    result.put("success", false);
                    result.put("message", "不支持的图片格式");
                    return ResponseEntity.badRequest().body(result);
                }
                
                String extensionFromContentType = getExtensionFromContentType(imageContentType);
                if (!extensionFromContentType.isEmpty()) {
                    imageOriginalFilename = imageOriginalFilename + "." + extensionFromContentType;
                }
            }

            String videoExtension = getFileExtension(videoOriginalFilename);
            if (!isValidVideoExtension(videoExtension)) {
                if (!isValidVideoContentType(videoContentType)) {
                    result.put("success", false);
                    result.put("message", "不支持的视频格式");
                    return ResponseEntity.badRequest().body(result);
                }
                
                String extensionFromContentType = getExtensionFromContentType(videoContentType);
                if (!extensionFromContentType.isEmpty()) {
                    videoOriginalFilename = videoOriginalFilename + "." + extensionFromContentType;
                }
            }

            String livePhotoDirPath = storageConfig.getTargetUploadDir(folder);
            File uploadDir = new File(livePhotoDirPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    result.put("success", false);
                    result.put("message", "创建上传目录失败，请检查应用权限");
                    result.put("path", uploadDir.getAbsolutePath());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }

            String imageFilePath = livePhotoDirPath + imageOriginalFilename;
            String videoFilePath = livePhotoDirPath + videoOriginalFilename;
            File imageTargetFile = new File(imageFilePath);
            File videoTargetFile = new File(videoFilePath);
            
            File imageParent = imageTargetFile.getParentFile();
            if (imageParent != null && !imageParent.exists()) {
                imageParent.mkdirs();
            }
            
            if (imageTargetFile.exists() && videoTargetFile.exists()) {
                result.put("success", true);
                result.put("message", "Live Photo 已存在，无需重复上传");
                result.put("existed", true);
                
                Map<String, Object> imageResult = new HashMap<>();
                imageResult.put("filename", imageOriginalFilename);
                imageResult.put("filePath", imageFilePath);
                imageResult.put("size", imageFile.getSize());
                result.put("image", imageResult);
                
                Map<String, Object> videoResult = new HashMap<>();
                videoResult.put("filename", videoOriginalFilename);
                videoResult.put("filePath", videoFilePath);
                videoResult.put("size", videoFile.getSize());
                result.put("video", videoResult);
                
                return ResponseEntity.ok(result);
            }

            imageFile.transferTo(imageTargetFile);
            
            Long finalImageTimestamp = null;
            String imageTimestampSource = null;
            
            Long imageFileMetadataTimestamp = extractTimestampFromMetadata(imageTargetFile, imageOriginalFilename);
            if (imageFileMetadataTimestamp != null && imageFileMetadataTimestamp > 0) {
                finalImageTimestamp = imageFileMetadataTimestamp;
                imageTimestampSource = "file_metadata";
            }
            
            if (finalImageTimestamp == null && imageMetadata != null && imageMetadata.getDateTimeOriginal() != null) {
                Long parsedTimestamp = TimestampParser.parseTimestamp(imageMetadata.getDateTimeOriginal());
                if (parsedTimestamp != null) {
                    finalImageTimestamp = parsedTimestamp;
                    imageTimestampSource = "metadata_dateTimeOriginal";
                }
            }
            
            if (finalImageTimestamp == null && imageMetadata != null && imageMetadata.getCreationDate() != null) {
                Long parsedTimestamp = TimestampParser.parseTimestamp(imageMetadata.getCreationDate());
                if (parsedTimestamp != null) {
                    finalImageTimestamp = parsedTimestamp;
                    imageTimestampSource = "metadata_creationDate";
                }
            }
            
            if (finalImageTimestamp == null && imageTimestamp != null && imageTimestamp > 0) {
                finalImageTimestamp = imageTimestamp;
                imageTimestampSource = "client_param";
            }
            
            if (finalImageTimestamp != null) {
                setFileTimestamp(imageTargetFile, finalImageTimestamp);
            }

            videoFile.transferTo(videoTargetFile);
            
            Long finalVideoTimestamp = null;
            String videoTimestampSource = null;
            
            Long videoFileMetadataTimestamp = extractTimestampFromMetadata(videoTargetFile, videoOriginalFilename);
            if (videoFileMetadataTimestamp != null && videoFileMetadataTimestamp > 0) {
                finalVideoTimestamp = videoFileMetadataTimestamp;
                videoTimestampSource = "file_metadata";
            }
            
            if (finalVideoTimestamp == null && videoMetadata != null && videoMetadata.getCreationDate() != null) {
                Long parsedTimestamp = TimestampParser.parseTimestamp(videoMetadata.getCreationDate());
                if (parsedTimestamp != null) {
                    finalVideoTimestamp = parsedTimestamp;
                    videoTimestampSource = "metadata_creationDate";
                }
            }
            
            if (finalVideoTimestamp == null && videoTimestamp != null && videoTimestamp > 0) {
                finalVideoTimestamp = videoTimestamp;
                videoTimestampSource = "client_param";
            }
            
            if (finalVideoTimestamp != null) {
                setFileTimestamp(videoTargetFile, finalVideoTimestamp);
            }

            invalidateBrowseCache(folder);
            
            result.put("success", true);
            result.put("message", "Live Photo 上传成功");
            result.put("existed", false);
            
            Map<String, Object> imageResult = new HashMap<>();
            imageResult.put("filename", imageOriginalFilename);
            imageResult.put("filePath", imageFilePath);
            imageResult.put("size", imageFile.getSize());
            imageResult.put("contentType", imageFile.getContentType());
            if (finalImageTimestamp != null) {
                imageResult.put("timestamp", finalImageTimestamp);
                imageResult.put("timestampDate", new Date(finalImageTimestamp).toString());
                imageResult.put("timestampSource", imageTimestampSource);
            }
            result.put("image", imageResult);
            
            Map<String, Object> videoResult = new HashMap<>();
            videoResult.put("filename", videoOriginalFilename);
            videoResult.put("filePath", videoFilePath);
            videoResult.put("size", videoFile.getSize());
            videoResult.put("contentType", videoFile.getContentType());
            if (finalVideoTimestamp != null) {
                videoResult.put("timestamp", finalVideoTimestamp);
                videoResult.put("timestampDate", new Date(finalVideoTimestamp).toString());
                videoResult.put("timestampSource", videoTimestampSource);
            }
            result.put("video", videoResult);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            logger.error("Live Photo 文件保存失败", e);
            result.put("success", false);
            result.put("message", "文件保存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } catch (Exception e) {
            logger.error("Live Photo 上传过程发生异常", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    private Long extractTimestampFromMetadata(File file, String originalFilename) {
        if (file == null || !file.exists()) {
            return null;
        }
        
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            TimeZone localTimeZone = TimeZone.getDefault();
            
            ExifSubIFDDirectory exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifSubIFD != null) {
                Date dateTimeOriginal = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, localTimeZone);
                if (dateTimeOriginal != null) {
                    return dateTimeOriginal.getTime();
                }
                
                Date dateTimeDigitized = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED, localTimeZone);
                if (dateTimeDigitized != null) {
                    return dateTimeDigitized.getTime();
                }
            }
            
            ExifIFD0Directory exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0 != null) {
                Date dateTime = exifIFD0.getDate(ExifIFD0Directory.TAG_DATETIME, localTimeZone);
                if (dateTime != null) {
                    return dateTime.getTime();
                }
            }
            
            QuickTimeDirectory quickTime = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
            if (quickTime != null) {
                Date creationTime = quickTime.getDate(QuickTimeDirectory.TAG_CREATION_TIME);
                if (creationTime != null) {
                    return creationTime.getTime();
                }
            }
            
            Mp4Directory mp4 = metadata.getFirstDirectoryOfType(Mp4Directory.class);
            if (mp4 != null) {
                Date creationTime = mp4.getDate(Mp4Directory.TAG_CREATION_TIME);
                if (creationTime != null) {
                    return creationTime.getTime();
                }
            }
            
            return null;
            
        } catch (ImageProcessingException e) {
            logger.debug("无法处理文件元数据: {} - {}", originalFilename, e.getMessage());
            return null;
        } catch (IOException e) {
            logger.warn("读取文件元数据时发生IO错误: {} - {}", originalFilename, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("提取文件时间戳时发生异常: {} - {}", originalFilename, e.getMessage());
            return null;
        }
    }

    private void setFileTimestamp(File file, Long timestamp) {
        if (file == null || !file.exists() || timestamp == null || timestamp <= 0) {
            return;
        }
        
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
                logger.debug("无法设置文件创建时间（文件系统可能不支持）: {}", e.getMessage());
            }
            
        } catch (IOException e) {
            logger.warn("设置文件时间戳失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> createMetadataInfo(VideoMetadata metadata) {
        Map<String, Object> info = new HashMap<>();
        
        if (metadata.getDuration() != null) info.put("duration", metadata.getDuration());
        if (metadata.getVideoWidth() != null && metadata.getVideoHeight() != null)
            info.put("resolution", metadata.getVideoWidth() + "x" + metadata.getVideoHeight());
        if (metadata.getVideoCodec() != null) info.put("videoCodec", metadata.getVideoCodec());
        if (metadata.getFrameRate() != null) info.put("frameRate", metadata.getFrameRate());
        if (metadata.getMake() != null) info.put("make", metadata.getMake());
        if (metadata.getModel() != null) info.put("model", metadata.getModel());
        if (metadata.getGpsLatitude() != null && metadata.getGpsLongitude() != null) {
            info.put("hasGPS", true);
            info.put("gps", String.format("%.6f, %.6f", metadata.getGpsLatitude(), metadata.getGpsLongitude()));
        }
        
        return info;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    private boolean isValidImageExtension(String extension) {
        if (extension == null || extension.isEmpty()) return false;
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    private boolean isValidVideoExtension(String extension) {
        if (extension == null || extension.isEmpty()) return false;
        return ALLOWED_VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    private boolean isValidImageContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) return false;
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();
        return ALLOWED_IMAGE_CONTENT_TYPES.contains(baseContentType);
    }
    
    private boolean isValidVideoContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) return false;
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();
        return ALLOWED_VIDEO_CONTENT_TYPES.contains(baseContentType);
    }

    private String getExtensionFromContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) return "";
        
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();
        
        switch (baseContentType) {
            case "image/jpeg": case "image/jpg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/bmp": return "bmp";
            case "image/webp": return "webp";
            case "image/heic": case "image/heic-sequence": return "heic";
            case "image/heif": case "image/heif-sequence": return "heif";
            case "video/quicktime": return "mov";
            case "video/mp4": return "mp4";
            case "video/x-msvideo": return "avi";
            case "video/x-matroska": return "mkv";
            default: return "";
        }
    }

    private String generateFilename(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        return uuid + "_" + timestamp + "." + extension;
    }

    @GetMapping("/server-info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getServerInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String ipAddress = getLocalLanIp();
            
            result.put("success", true);
            result.put("computerName", localHost.getHostName());
            result.put("hostName", localHost.getHostName());
            result.put("ipAddress", ipAddress);
            result.put("osName", System.getProperty("os.name"));
            result.put("osVersion", System.getProperty("os.version"));
            result.put("osArch", System.getProperty("os.arch"));
            result.put("userName", System.getProperty("user.name"));
            result.put("userHome", System.getProperty("user.home"));
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "无法获取服务器信息: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取本机局域网 IP 地址
     * 遍历网络接口，优先返回非回环的 IPv4 地址（解决 Linux/树莓派上 getLocalHost 返回 127.0.1.1 的问题）
     */
    private String getLocalLanIp() {
        try {
            String fallback = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                
                String name = ni.getName().toLowerCase();
                if (name.startsWith("utun") || name.startsWith("tun") || name.startsWith("tap") 
                        || name.startsWith("ppp") || name.startsWith("vmnet") || name.startsWith("vboxnet")
                        || name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth")) {
                    continue;
                }
                
                boolean isPhysical = name.startsWith("en") || name.startsWith("eth") || name.startsWith("wlan");
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress() || !(addr instanceof java.net.Inet4Address)) continue;
                    
                    if (isPhysical) {
                        return addr.getHostAddress();
                    }
                    if (fallback == null) {
                        fallback = addr.getHostAddress();
                    }
                }
            }
            if (fallback != null) {
                return fallback;
            }
        } catch (Exception e) {
            logger.warn("遍历网络接口失败: {}", e.getMessage());
        }
        
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
    
    // ========== 媒体根目录浏览相关接口 ==========
    
    @GetMapping("/roots")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoots() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (mediaBrowseConfig == null) {
                result.put("success", false);
                result.put("error", "根目录服务未初始化");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
            }
            
            List<MediaRoot> roots = mediaBrowseConfig.getRoots();
            
            for (MediaRoot root : roots) {
                File rootDir = new File(root.getPath());
                if (rootDir.exists() && rootDir.isDirectory()) {
                    int[] stats = calculateDirStats(rootDir);
                    root.setItemCount(stats[0]);
                    root.setTotalSize((long) stats[1]);
                    root.setLastModified(rootDir.lastModified());
                }
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("roots", roots);
            
            result.put("success", true);
            result.put("data", data);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取媒体根目录失败", e);
            result.put("success", false);
            result.put("error", "获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/roots/reload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reloadRoots() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (mediaBrowseConfig == null) {
                result.put("success", false);
                result.put("error", "根目录服务未初始化");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
            }
            
            mediaBrowseConfig.reloadRoots();
            List<MediaRoot> roots = mediaBrowseConfig.getRoots();
            
            result.put("success", true);
            result.put("message", "配置已刷新");
            result.put("count", roots.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("刷新根目录配置失败", e);
            result.put("success", false);
            result.put("error", "刷新失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/roots/{rootId}/browse")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> browseRoot(
            @PathVariable String rootId,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam(defaultValue = "false") boolean includeDetails,
            @RequestParam(defaultValue = "-1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        
        try {
            if (fileBrowseService == null) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "文件浏览服务未初始化");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult);
            }
            
            Map<String, Object> result = fileBrowseService.browse(rootId, path, includeDetails, page, pageSize);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", result);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            logger.error("浏览目录失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "浏览失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    @GetMapping("/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(
            @RequestParam String rootId,
            @RequestParam String fileId) {
        
        try {
            if (fileBrowseService == null || thumbnailService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            
            File file = fileBrowseService.getFileById(rootId, fileId);
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] thumbnailData = thumbnailService.generateThumbnail(file);
            
            if (thumbnailData == null) {
                try {
                    byte[] originalData = java.nio.file.Files.readAllBytes(file.toPath());
                    String contentTypeStr = guessContentType(file.getName());
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentTypeStr))
                            .body(originalData);
                } catch (IOException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(thumbnailData);
            
        } catch (Exception e) {
            logger.error("获取缩略图失败 - rootId: {}, fileId: {}", rootId, fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/download")
    public void downloadFile(
            @RequestParam String rootId,
            @RequestParam String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        try {
            if (fileBrowseService == null) {
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }
            
            File file = fileBrowseService.getFileById(rootId, fileId);
            if (file == null || !file.exists()) {
                response.sendError(HttpStatus.NOT_FOUND.value());
                return;
            }
            
            long fileSize = file.length();
            String fileName = file.getName();
            String ext = getFileExtension(fileName).toLowerCase();
            
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (ext.equals("jpg") || ext.equals("jpeg")) contentType = MediaType.IMAGE_JPEG;
            else if (ext.equals("png")) contentType = MediaType.IMAGE_PNG;
            else if (ext.equals("gif")) contentType = MediaType.IMAGE_GIF;
            else if (ext.equals("mp4")) contentType = MediaType.parseMediaType("video/mp4");
            else if (ext.equals("mov")) contentType = MediaType.parseMediaType("video/quicktime");
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequestStreaming(request, response, file, rangeHeader, fileSize, fileName, contentType);
                return;
            }
            
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(contentType.toString());
            response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(fileSize));
            
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                 ServletOutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
            }
            
        } catch (Exception e) {
            logger.error("下载文件失败 - rootId: {}, fileId: {}", rootId, fileId, e);
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
    }
    
    private void handleRangeRequestStreaming(
            HttpServletRequest request,
            HttpServletResponse response,
            File file, 
            String rangeHeader, 
            long fileSize, 
            String fileName, 
            MediaType contentType) throws IOException {
        
        try {
            String range = rangeHeader.substring("bytes=".length());
            String[] parts = range.split("-");
            
            long rangeStart = Long.parseLong(parts[0]);
            long rangeEnd = parts.length > 1 && !parts[1].isEmpty() 
                    ? Long.parseLong(parts[1]) 
                    : fileSize - 1;
            
            if (rangeStart < 0 || rangeStart >= fileSize || rangeEnd >= fileSize || rangeStart > rangeEnd) {
                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }
            
            long contentLength = rangeEnd - rangeStart + 1;
            
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setContentType(contentType.toString());
            response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize);
            response.setHeader("Content-Length", String.valueOf(contentLength));
            
            try (java.io.RandomAccessFile randomAccessFile = new java.io.RandomAccessFile(file, "r");
                 ServletOutputStream outputStream = response.getOutputStream()) {
                
                randomAccessFile.seek(rangeStart);
                
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = randomAccessFile.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    
                    outputStream.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
                
                outputStream.flush();
            }
            
        } catch (Exception e) {
            logger.error("处理 Range 请求失败 - 文件: {}, Range: {}", fileName, rangeHeader, e);
            throw e;
        }
    }
    
    private String guessContentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        else if (lowerName.endsWith(".png")) return "image/png";
        else if (lowerName.endsWith(".gif")) return "image/gif";
        else if (lowerName.endsWith(".webp")) return "image/webp";
        else if (lowerName.endsWith(".heic") || lowerName.endsWith(".heif")) return "image/heic";
        else if (lowerName.endsWith(".bmp")) return "image/bmp";
        else return "application/octet-stream";
    }
    
    private int[] calculateDirStats(File dir) {
        int count = 0;
        long size = 0;
        
        File[] files = dir.listFiles();
        if (files == null) return new int[]{0, 0};
        
        Set<String> processedLivePhotoBaseNames = new HashSet<>();
        Map<String, File> imageFiles = new HashMap<>();
        Map<String, File> movFiles = new HashMap<>();
        
        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            
            String name = file.getName();
            String lowerName = name.toLowerCase();
            
            if (file.isFile()) {
                String baseName = getFileBaseName(name);
                
                if (lowerName.endsWith(".heic") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    imageFiles.put(baseName.toLowerCase(), file);
                } else if (lowerName.endsWith(".mov")) {
                    movFiles.put(baseName.toLowerCase(), file);
                }
            }
        }
        
        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            
            String name = file.getName();
            String lowerName = name.toLowerCase();
            String baseName = getFileBaseName(name).toLowerCase();
            
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
    
    private String getFileBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) return fileName.substring(0, lastDot);
        return fileName;
    }
    
    private void invalidateBrowseCache(String folder) {
        if (directoryCacheService != null) {
            directoryCacheService.clear();
            logger.info("上传完成，已清除浏览缓存 (folder={})", folder);
        }
    }
    
    
    @GetMapping("/upload/folders")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUploadFolders() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String baseDir = storageConfig.getUploadBaseDir();
            File baseDirFile = new File(baseDir);
            
            if (!baseDirFile.exists()) {
                baseDirFile.mkdirs();
            }
            
            List<Map<String, Object>> folders = new ArrayList<>();
            
            Map<String, Object> rootFolder = new HashMap<>();
            rootFolder.put("name", "根目录");
            rootFolder.put("path", "");
            rootFolder.put("isRoot", true);
            folders.add(rootFolder);
            
            File[] subDirs = baseDirFile.listFiles(File::isDirectory);
            if (subDirs != null) {
                Arrays.sort(subDirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File dir : subDirs) {
                    if (dir.getName().startsWith(".")) continue;
                    
                    Map<String, Object> folderInfo = new HashMap<>();
                    folderInfo.put("name", dir.getName());
                    folderInfo.put("path", dir.getName());
                    folderInfo.put("isRoot", false);
                    
                    File[] dirFiles = dir.listFiles();
                    int fileCount = 0;
                    
                    if (dirFiles != null) {
                        Set<String> countedLivePhotos = new HashSet<>();
                        for (File f : dirFiles) {
                            if (f.isFile() && !f.getName().startsWith(".")) {
                                String name = f.getName().toLowerCase();
                                String baseName = getFileBaseName(f.getName()).toLowerCase();
                                
                                boolean isLivePhotoPart = false;
                                if (name.endsWith(".heic") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                                    File movFile = new File(dir, baseName + ".mov");
                                    File movFileUpper = new File(dir, baseName + ".MOV");
                                    if (movFile.exists() || movFileUpper.exists()) {
                                        isLivePhotoPart = true;
                                        if (!countedLivePhotos.contains(baseName)) {
                                            countedLivePhotos.add(baseName);
                                            fileCount++;
                                        }
                                    }
                                } else if (name.endsWith(".mov")) {
                                    File heicFile = new File(dir, baseName + ".heic");
                                    File heicFileUpper = new File(dir, baseName + ".HEIC");
                                    File jpgFile = new File(dir, baseName + ".jpg");
                                    File jpgFileUpper = new File(dir, baseName + ".JPG");
                                    if (heicFile.exists() || heicFileUpper.exists() || 
                                        jpgFile.exists() || jpgFileUpper.exists()) {
                                        isLivePhotoPart = true;
                                    }
                                }
                                
                                if (!isLivePhotoPart) {
                                    fileCount++;
                                }
                            }
                        }
                    }
                    folderInfo.put("fileCount", fileCount);
                    
                    folders.add(folderInfo);
                }
            }
            
            result.put("success", true);
            result.put("baseDir", baseDir);
            result.put("folders", folders);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取上传文件夹列表失败", e);
            result.put("success", false);
            result.put("error", "获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/upload/folders/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createUploadFolder(@RequestParam("name") String folderName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (folderName == null || folderName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "文件夹名称不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            String safeName = folderName.trim()
                .replaceAll("[/\\\\:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
            
            if (safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")) {
                result.put("success", false);
                result.put("error", "无效的文件夹名称");
                return ResponseEntity.badRequest().body(result);
            }
            
            String baseDir = storageConfig.getUploadBaseDir();
            File newFolder = new File(baseDir, safeName);
            
            if (newFolder.exists()) {
                result.put("success", false);
                result.put("error", "文件夹已存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (newFolder.mkdirs()) {
                result.put("success", true);
                result.put("name", safeName);
                result.put("path", safeName);
                result.put("message", "文件夹创建成功");
                
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("error", "创建文件夹失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            
        } catch (Exception e) {
            logger.error("创建上传文件夹失败", e);
            result.put("success", false);
            result.put("error", "创建失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
