package com.upload.picture.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缩略图生成服务
 * 支持常规图片格式 + HEIF/HEIC 格式（通过libheif命令行工具）+ 视频（FFmpeg）
 */
@Service
public class ThumbnailService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    
    private static final int THUMBNAIL_SIZE = 400;
    private static final float JPEG_QUALITY = 0.90f;
    
    private static final List<String> HEIF_EXTENSIONS = Arrays.asList(".heic", ".heif");
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(".mp4", ".mov", ".avi", ".mkv", ".m4v");
    private static final List<String> STANDARD_EXTENSIONS = Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    );
    
    private static final String HEIF_THUMBNAILER = "/usr/local/bin/heif-thumbnailer";
    private static final String HEIF_CONVERT = "/usr/local/bin/heif-convert";
    private static final String FFMPEG_CUSTOM = "/Users/linsong.chen/document/ffmpeg/ffmpeg";
    private static final String FFMPEG = "/opt/homebrew/bin/ffmpeg";
    private static final String FFMPEG_INTEL = "/usr/local/bin/ffmpeg";
    
    public byte[] generateThumbnail(File imageFile) throws IOException {
        if (!imageFile.exists()) {
            throw new FileNotFoundException("图片文件不存在: " + imageFile.getAbsolutePath());
        }
        
        String fileName = imageFile.getName().toLowerCase();
        
        if (isVideoFormat(fileName)) {
            logger.info("检测到视频格式: {}", fileName);
            return generateVideoThumbnail(imageFile);
        }
        
        if (isHeifFormat(fileName)) {
            logger.info("检测到HEIF格式图片: {}", fileName);
            
            if (!isCommandAvailable(HEIF_THUMBNAILER) && !isCommandAvailable(HEIF_CONVERT)) {
                String error = "libheif工具不可用，请安装: brew install libheif";
                logger.error(error);
                throw new IOException(error);
            }
            
            return generateHeifThumbnail(imageFile);
        }
        
        logger.debug("处理标准格式图片: {}", fileName);
        return generateStandardThumbnail(imageFile);
    }
    
    private boolean isHeifFormat(String fileName) {
        return HEIF_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private String detectActualFileType(File file) {
        try {
            byte[] header = new byte[12];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead = fis.read(header);
                if (bytesRead < 2) {
                    return "unknown";
                }
            }
            
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) {
                return "jpeg";
            }
            
            if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
                (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47) {
                return "png";
            }
            
            if (header.length >= 12) {
                String ftypSignature = new String(header, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                if ("ftyp".equals(ftypSignature)) {
                    String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (brand.startsWith("heic") || brand.startsWith("heix") || 
                        brand.startsWith("hevc") || brand.startsWith("mif1")) {
                        return "heic";
                    }
                }
            }
            
            return "unknown";
            
        } catch (IOException e) {
            logger.error("检测文件格式失败: {}", e.getMessage());
            return "unknown";
        }
    }
    
    private boolean isVideoFormat(String fileName) {
        return VIDEO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private byte[] generateHeifThumbnail(File heifFile) throws IOException {
        String actualFormat = detectActualFileType(heifFile);
        String fileName = heifFile.getName();
        
        logger.info("文件: {}, 实际格式: {}", fileName, actualFormat);
        
        if ("jpeg".equals(actualFormat) || "png".equals(actualFormat)) {
            logger.info("检测到文件实际是 {}，使用ImageIO处理", actualFormat.toUpperCase());
            return generateStandardThumbnail(heifFile);
        }
        
        if (!"heic".equals(actualFormat)) {
            logger.warn("未知格式，尝试使用ImageIO处理");
            try {
                return generateStandardThumbnail(heifFile);
            } catch (IOException e) {
                logger.warn("ImageIO处理失败，继续尝试heif-thumbnailer: {}", e.getMessage());
            }
        }
        
        File tempJpeg = null;
        
        try {
            tempJpeg = File.createTempFile("heif_thumb_", ".jpg");
            
            boolean success = tryHeifThumbnailer(heifFile, tempJpeg);
            
            if (!success) {
                logger.warn("heif-thumbnailer失败，尝试使用heif-convert");
                success = tryHeifConvert(heifFile, tempJpeg);
            }
            
            if (!success) {
                logger.warn("libheif工具都失败了，最后尝试ImageIO回退");
                try {
                    return generateStandardThumbnail(heifFile);
                } catch (IOException e) {
                    logger.error("ImageIO回退也失败: {}", e.getMessage());
                    throw new IOException("无法处理HEIF文件: " + heifFile.getName());
                }
            }
            
            byte[] thumbnailData = Files.readAllBytes(tempJpeg.toPath());
            logger.info("HEIF缩略图生成成功: {} -> {} bytes", heifFile.getName(), thumbnailData.length);
            
            return thumbnailData;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("生成HEIF缩略图被中断", e);
            
        } finally {
            if (tempJpeg != null && tempJpeg.exists()) {
                try {
                    Files.delete(tempJpeg.toPath());
                } catch (IOException e) {
                    logger.warn("删除临时文件失败: {}", tempJpeg.getAbsolutePath(), e);
                }
            }
        }
    }
    
    private boolean tryHeifThumbnailer(File inputFile, File outputFile) 
            throws IOException, InterruptedException {
        
        if (!isCommandAvailable(HEIF_THUMBNAILER)) {
            return false;
        }
        
        ProcessBuilder pb = new ProcessBuilder(
            HEIF_THUMBNAILER,
            inputFile.getAbsolutePath(),
            outputFile.getAbsolutePath(),
            String.valueOf(THUMBNAIL_SIZE)
        );
        
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            logger.error("heif-thumbnailer失败 (退出码: {})", exitCode);
            return false;
        }
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            return false;
        }
        
        return true;
    }
    
    private boolean tryHeifConvert(File inputFile, File outputFile) 
            throws IOException, InterruptedException {
        
        if (!isCommandAvailable(HEIF_CONVERT)) {
            return false;
        }
        
        File tempFullJpeg = null;
        
        try {
            tempFullJpeg = File.createTempFile("heif_full_", ".jpg");
            
            ProcessBuilder pb = new ProcessBuilder(
                HEIF_CONVERT,
                "-q", "90",
                inputFile.getAbsolutePath(),
                tempFullJpeg.getAbsolutePath()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode != 0) {
                return false;
            }
            
            if (!tempFullJpeg.exists() || tempFullJpeg.length() == 0) {
                return false;
            }
            
            BufferedImage fullImage = ImageIO.read(tempFullJpeg);
            if (fullImage == null) {
                return false;
            }
            
            BufferedImage thumbnail = resizeImage(fullImage, THUMBNAIL_SIZE);
            
            if (!ImageIO.write(thumbnail, "jpg", outputFile)) {
                return false;
            }
            
            return true;
            
        } finally {
            if (tempFullJpeg != null && tempFullJpeg.exists()) {
                try {
                    Files.delete(tempFullJpeg.toPath());
                } catch (IOException e) {
                    logger.warn("删除临时文件失败: {}", tempFullJpeg.getAbsolutePath(), e);
                }
            }
        }
    }
    
    private boolean isCommandAvailable(String command) {
        try {
            File commandFile = new File(command);
            if (commandFile.exists() && commandFile.canExecute()) {
                return true;
            }
            
            ProcessBuilder pb = new ProcessBuilder("which", command);
            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            return process.exitValue() == 0;
            
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
    
    private byte[] generateStandardThumbnail(File imageFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(imageFile);
        
        if (originalImage == null) {
            throw new IOException("无法读取图片: " + imageFile.getName());
        }
        
        BufferedImage thumbnail = resizeImage(originalImage, THUMBNAIL_SIZE);
        byte[] thumbnailData = encodeAsJpeg(thumbnail, JPEG_QUALITY);
        
        logger.debug("标准缩略图生成成功: {} -> {} bytes ({}x{})", 
                    imageFile.getName(), thumbnailData.length,
                    thumbnail.getWidth(), thumbnail.getHeight());
        
        return thumbnailData;
    }
    
    private byte[] encodeAsJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        
        javax.imageio.plugins.jpeg.JPEGImageWriteParam param = 
            new javax.imageio.plugins.jpeg.JPEGImageWriteParam(null);
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        
        writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        
        ios.close();
        writer.dispose();
        
        return baos.toByteArray();
    }
    
    private BufferedImage resizeImage(BufferedImage originalImage, int targetSize) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        double scale = Math.max(
            (double) targetSize / width,
            (double) targetSize / height
        );
        
        int scaledWidth = (int) Math.ceil(width * scale);
        int scaledHeight = (int) Math.ceil(height * scale);
        
        BufferedImage scaledImage = new BufferedImage(
            scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        int cropX = Math.max(0, (scaledWidth - targetSize) / 2);
        int cropY = Math.max(0, (scaledHeight - targetSize) / 2);
        int cropWidth = Math.min(targetSize, scaledWidth - cropX);
        int cropHeight = Math.min(targetSize, scaledHeight - cropY);
        
        BufferedImage thumbnail = scaledImage.getSubimage(cropX, cropY, cropWidth, cropHeight);
        
        BufferedImage result = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(thumbnail, 0, 0, null);
        g.dispose();
        
        return result;
    }
    
    private byte[] generateVideoThumbnail(File videoFile) throws IOException {
        File tempFrame = null;
        
        try {
            tempFrame = File.createTempFile("video_thumb_", ".jpg");
            
            boolean success = extractVideoFrame(videoFile, tempFrame);
            
            if (!success) {
                logger.warn("FFmpeg提取失败，生成占位图: {}", videoFile.getName());
                return generateVideoPlaceholder();
            }
            
            byte[] thumbnailData = Files.readAllBytes(tempFrame.toPath());
            logger.info("视频缩略图生成成功: {} -> {} bytes", videoFile.getName(), thumbnailData.length);
            
            return thumbnailData;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("生成视频缩略图被中断", e);
            
        } finally {
            if (tempFrame != null && tempFrame.exists()) {
                try {
                    Files.delete(tempFrame.toPath());
                } catch (IOException e) {
                    logger.warn("删除临时文件失败: {}", tempFrame.getAbsolutePath(), e);
                }
            }
        }
    }
    
    private byte[] generateVideoPlaceholder() throws IOException {
        BufferedImage placeholder = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = placeholder.createGraphics();
        
        g2d.setColor(new Color(50, 50, 50));
        g2d.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        
        g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = THUMBNAIL_SIZE / 2;
        int centerY = THUMBNAIL_SIZE / 2;
        int iconSize = 80;
        
        int[] xPoints = { centerX - iconSize/3, centerX + iconSize*2/3, centerX - iconSize/3 };
        int[] yPoints = { centerY - iconSize/2, centerY, centerY + iconSize/2 };
        
        g2d.fillPolygon(xPoints, yPoints, 3);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        String text = "VIDEO";
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, (THUMBNAIL_SIZE - textWidth) / 2, THUMBNAIL_SIZE - 30);
        
        g2d.dispose();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(placeholder, "jpg", baos)) {
            throw new IOException("生成占位图失败");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 提取视频帧，3级回退策略：
     * 1. 标准方法（居中裁剪）
     * 2. 兼容模式（单线程，适用于iPhone HEVC）
     * 3. 旧版API（最简单命令）
     */
    private boolean extractVideoFrame(File videoFile, File outputFile) 
            throws IOException, InterruptedException {
        
        String ffmpegPath = detectFFmpegPath();
        
        if (ffmpegPath == null) {
            logger.error("FFmpeg不可用，请安装: brew install ffmpeg");
            return false;
        }
        
        logger.info("使用FFmpeg提取视频帧: {}", videoFile.getName());
        logger.info("视频文件路径: {}", videoFile.getAbsolutePath());
        logger.info("视频文件大小: {} bytes", videoFile.length());
        
        boolean success = tryExtractWithStandardMethod(ffmpegPath, videoFile, outputFile);
        
        if (!success) {
            logger.warn("标准方法失败，尝试兼容模式");
            success = tryExtractWithCompatibilityMode(ffmpegPath, videoFile, outputFile);
        }
        
        return success;
    }
    
    /**
     * 策略1: 标准方法（居中裁剪成正方形）
     */
    private boolean tryExtractWithStandardMethod(String ffmpegPath, File videoFile, File outputFile)
            throws IOException, InterruptedException {
        
        String cropFilter = String.format(
            "scale='if(gt(iw,ih),-1,%d)':'if(gt(iw,ih),%d,-1)',crop=%d:%d",
            THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE
        );
        
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath,
            "-i", videoFile.getAbsolutePath(),
            "-vframes", "1",
            "-vf", cropFilter,
            "-q:v", "2",
            "-y",
            outputFile.getAbsolutePath()
        );
        
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            logger.error("FFmpeg超时（20秒）");
            return false;
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            logger.error("FFmpeg失败 (退出码: {})", exitCode);
            logger.error("视频文件: {}", videoFile.getAbsolutePath());
            
            String outputStr = output.toString().toLowerCase();
            if (outputStr.contains("invalid data found")) {
                logger.error("提示: 视频文件可能损坏或格式不支持");
            } else if (outputStr.contains("no such file")) {
                logger.error("提示: 文件路径可能包含特殊字符或不存在");
            } else if (outputStr.contains("permission denied")) {
                logger.error("提示: 文件权限不足");
            }
            
            return false;
        }
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            logger.error("FFmpeg未生成有效文件");
            return false;
        }
        
        logger.info("FFmpeg标准方法成功: {} bytes", outputFile.length());
        return true;
    }
    
    /**
     * 策略2: 兼容模式（单线程，适用于iPhone HEVC视频）
     */
    private boolean tryExtractWithCompatibilityMode(String ffmpegPath, File videoFile, File outputFile)
            throws IOException, InterruptedException {
        
        logger.info("使用兼容模式处理: {}", videoFile.getName());
        
        String cropFilter = String.format(
            "scale='if(gt(iw,ih),-1,%d)':'if(gt(iw,ih),%d,-1)',crop=%d:%d",
            THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE
        );
        
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath,
            "-filter_threads", "1",
            "-threads", "1",
            "-i", videoFile.getAbsolutePath(),
            "-an",
            "-vframes", "1",
            "-vf", cropFilter,
            "-q:v", "2",
            "-y",
            outputFile.getAbsolutePath()
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            logger.error("FFmpeg兼容模式超时");
            return false;
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            logger.error("FFmpeg兼容模式也失败 (退出码: {})", exitCode);
            return tryExtractWithLegacyMethod(ffmpegPath, videoFile, outputFile);
        }
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            logger.error("兼容模式未生成有效文件");
            return false;
        }
        
        logger.info("兼容模式成功: {} bytes", outputFile.length());
        return true;
    }
    
    /**
     * 策略3: 旧版API方法（最简单的命令，避免新API的bug）
     */
    private boolean tryExtractWithLegacyMethod(String ffmpegPath, File videoFile, File outputFile)
            throws IOException, InterruptedException {
        
        logger.info("使用旧版API模式处理: {}", videoFile.getName());
        
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath,
            "-hide_banner",
            "-loglevel", "error",
            "-i", videoFile.getAbsolutePath(),
            "-f", "image2",
            "-vframes", "1",
            "-s", THUMBNAIL_SIZE + "x" + THUMBNAIL_SIZE,
            "-y",
            outputFile.getAbsolutePath()
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            logger.error("旧版API模式超时");
            return false;
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            logger.error("旧版API模式也失败 (退出码: {})", exitCode);
            if (output.length() > 0) {
                logger.error("输出: {}", output.toString());
            }
            return false;
        }
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            logger.error("旧版API模式未生成有效文件");
            return false;
        }
        
        logger.info("旧版API模式成功: {} bytes", outputFile.length());
        return true;
    }
    
    /**
     * 检测FFmpeg路径，按优先级：自定义路径 > Apple Silicon > Intel > which查找
     */
    private String detectFFmpegPath() {
        File ffmpegCustom = new File(FFMPEG_CUSTOM);
        if (ffmpegCustom.exists() && ffmpegCustom.canExecute()) {
            logger.info("找到FFmpeg (自定义): {}", FFMPEG_CUSTOM);
            return FFMPEG_CUSTOM;
        }
        
        File ffmpegApple = new File(FFMPEG);
        if (ffmpegApple.exists() && ffmpegApple.canExecute()) {
            logger.info("找到FFmpeg (Apple Silicon): {}", FFMPEG);
            return FFMPEG;
        }
        
        File ffmpegIntel = new File(FFMPEG_INTEL);
        if (ffmpegIntel.exists() && ffmpegIntel.canExecute()) {
            logger.info("找到FFmpeg (Intel): {}", FFMPEG_INTEL);
            return FFMPEG_INTEL;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "ffmpeg");
            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String path = reader.readLine();
                    if (path != null && !path.isEmpty()) {
                        logger.info("找到FFmpeg: {}", path);
                        return path;
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.debug("which命令查找FFmpeg失败", e);
        }
        
        logger.warn("FFmpeg不可用");
        return null;
    }
}
