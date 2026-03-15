package com.upload.picture.util;

import com.upload.picture.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 视频元数据工具类
 * 主要用于从 iOS 上传的元数据中提取创建时间
 */
public class VideoMetadataWriter {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataWriter.class);
    
    public static Long parseCreationDate(String creationDateStr) {
        if (creationDateStr == null || creationDateStr.isEmpty()) {
            return null;
        }
        
        SimpleDateFormat[] formats = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        };
        
        formats[0].setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        formats[3].setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        
        for (SimpleDateFormat format : formats) {
            try {
                Date date = format.parse(creationDateStr);
                logger.debug("成功解析创建日期: {} -> {}", creationDateStr, date);
                return date.getTime();
            } catch (Exception e) {
                // continue
            }
        }
        
        logger.warn("无法解析创建日期: {}", creationDateStr);
        return null;
    }
    
    public static String formatMetadataInfo(VideoMetadata metadata) {
        if (metadata == null) {
            return "无元数据";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("视频元数据信息:\n");
        
        if (metadata.getDuration() != null) {
            sb.append(String.format("  时长: %.2f 秒\n", metadata.getDuration()));
        }
        if (metadata.getFileSize() != null) {
            sb.append(String.format("  文件大小: %d 字节\n", metadata.getFileSize()));
        }
        if (metadata.getCreationDate() != null) {
            sb.append(String.format("  创建日期: %s\n", metadata.getCreationDate()));
        }
        if (metadata.getVideoWidth() != null && metadata.getVideoHeight() != null) {
            sb.append(String.format("  视频分辨率: %dx%d\n", metadata.getVideoWidth(), metadata.getVideoHeight()));
        }
        if (metadata.getVideoCodec() != null) {
            sb.append(String.format("  视频编码: %s\n", metadata.getVideoCodec()));
        }
        if (metadata.getVideoBitrate() != null) {
            sb.append(String.format("  视频比特率: %d bps\n", metadata.getVideoBitrate()));
        }
        if (metadata.getFrameRate() != null) {
            sb.append(String.format("  帧率: %.2f fps\n", metadata.getFrameRate()));
        }
        if (metadata.getAudioCodec() != null) {
            sb.append(String.format("  音频编码: %s\n", metadata.getAudioCodec()));
        }
        if (metadata.getAudioChannels() != null) {
            sb.append(String.format("  音频声道: %d\n", metadata.getAudioChannels()));
        }
        if (metadata.getAudioSampleRate() != null) {
            sb.append(String.format("  采样率: %.0f Hz\n", metadata.getAudioSampleRate()));
        }
        if (metadata.getGpsLatitude() != null && metadata.getGpsLongitude() != null) {
            sb.append(String.format("  GPS 位置: %.6f, %.6f\n",
                metadata.getGpsLatitude(), metadata.getGpsLongitude()));
        }
        if (metadata.getGpsAltitude() != null) {
            sb.append(String.format("  GPS 高度: %.2f 米\n", metadata.getGpsAltitude()));
        }
        if (metadata.getMake() != null) {
            sb.append(String.format("  制造商: %s\n", metadata.getMake()));
        }
        if (metadata.getModel() != null) {
            sb.append(String.format("  设备型号: %s\n", metadata.getModel()));
        }
        if (metadata.getSoftware() != null) {
            sb.append(String.format("  软件版本: %s\n", metadata.getSoftware()));
        }
        
        return sb.toString();
    }
}
