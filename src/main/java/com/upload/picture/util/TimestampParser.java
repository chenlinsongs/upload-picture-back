package com.upload.picture.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 时间戳解析工具类
 * 支持多种时间格式的自动检测和解析
 */
public class TimestampParser {
    
    private static final Logger logger = LoggerFactory.getLogger(TimestampParser.class);
    
    public static Long parseTimestamp(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = timeString.trim();
        logger.debug("尝试解析时间字符串: {}", trimmed);
        
        Long timestamp = null;
        
        timestamp = tryParseISO8601(trimmed);
        if (timestamp != null) {
            logger.debug("解析为 ISO8601 格式: {} -> {}", trimmed, new Date(timestamp));
            return timestamp;
        }
        
        timestamp = tryParseEXIF(trimmed);
        if (timestamp != null) {
            logger.debug("解析为 EXIF 格式: {} -> {}", trimmed, new Date(timestamp));
            return timestamp;
        }
        
        timestamp = tryParseStandard(trimmed);
        if (timestamp != null) {
            logger.debug("解析为标准格式: {} -> {}", trimmed, new Date(timestamp));
            return timestamp;
        }
        
        logger.warn("无法解析时间字符串: {}", trimmed);
        return null;
    }
    
    private static Long tryParseISO8601(String timeString) {
        SimpleDateFormat[] formats = {
            createFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "UTC"),
            createFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", null),
            createFormat("yyyy-MM-dd'T'HH:mm:ssXXX", null),
            createFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", "UTC"),
            createFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", null),
            createFormat("yyyy-MM-dd'T'HH:mm:ss", null)
        };
        
        return tryParseWithFormats(timeString, formats);
    }
    
    private static Long tryParseEXIF(String timeString) {
        SimpleDateFormat[] formats = {
            createFormat("yyyy:MM:dd HH:mm:ss", null),
            createFormat("yyyy-MM-dd HH:mm:ss", null),
            createFormat("yyyy:MM:dd", null),
            createFormat("yyyy-MM-dd", null)
        };
        
        return tryParseWithFormats(timeString, formats);
    }
    
    private static Long tryParseStandard(String timeString) {
        SimpleDateFormat[] formats = {
            createFormat("yyyy/MM/dd HH:mm:ss", null),
            createFormat("yyyy/MM/dd", null),
            createFormat("MM/dd/yyyy HH:mm:ss", null),
            createFormat("MM/dd/yyyy", null),
            createFormat("dd-MM-yyyy HH:mm:ss", null),
            createFormat("dd-MM-yyyy", null)
        };
        
        return tryParseWithFormats(timeString, formats);
    }
    
    private static Long tryParseWithFormats(String timeString, SimpleDateFormat[] formats) {
        for (SimpleDateFormat format : formats) {
            try {
                Date date = format.parse(timeString);
                return date.getTime();
            } catch (Exception e) {
                // continue
            }
        }
        return null;
    }
    
    private static SimpleDateFormat createFormat(String pattern, String timeZoneId) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        if (timeZoneId != null) {
            format.setTimeZone(TimeZone.getTimeZone(timeZoneId));
        } else {
            format.setTimeZone(TimeZone.getDefault());
        }
        return format;
    }
}
