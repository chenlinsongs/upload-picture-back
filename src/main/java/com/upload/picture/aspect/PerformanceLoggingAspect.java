package com.upload.picture.aspect;

import com.alibaba.fastjson.JSON;
import com.upload.picture.service.RootConfigService;
import com.upload.picture.model.MediaRoot;
import com.upload.picture.model.FileSystemItem;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

/**
 * 性能日志切面
 * 自动记录Controller方法的耗时、入参和结果
 */
@Aspect
@Component
public class PerformanceLoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceLoggingAspect.class);
    
    @Autowired(required = false)
    private RootConfigService rootConfigService;
    
    @Pointcut("execution(* com.upload.picture.controller..*.*(..))")
    public void controllerMethods() {}
    
    @Pointcut("execution(* com.upload.picture.service..*.*(..))")
    public void serviceMethods() {}
    
    @Around("controllerMethods()")
    public Object logControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "Controller");
    }
    
    private Object logPerformance(ProceedingJoinPoint joinPoint, String layerType) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        
        long startTime = System.currentTimeMillis();
        String extraInfo = logMethodParameters(joinPoint, className, methodName, layerType);
        
        Object result = null;
        Throwable exception = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logMethodResult(className, methodName, duration, result, exception, extraInfo);
        }
    }
    
    private String logMethodParameters(ProceedingJoinPoint joinPoint, String className, 
                                       String methodName, String layerType) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        
        StringBuilder paramLog = new StringBuilder();
        paramLog.append("[").append(className).append(".").append(methodName).append("] 入参: ");
        
        String rootId = null;
        String extraInfo = null;
        
        if (parameters.length == 0) {
            paramLog.append("无参数");
        } else {
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) paramLog.append(", ");
                
                Parameter param = parameters[i];
                Object arg = args[i];
                String paramName = getParameterName(param);
                
                if ("rootId".equals(paramName) && arg instanceof String) {
                    rootId = (String) arg;
                }
                
                paramLog.append(paramName).append("=").append(formatParameterValue(arg));
            }
        }
        
        if ("FileBrowseService".equals(className) && "browse".equals(methodName) && rootId != null) {
            if (rootConfigService != null) {
                MediaRoot root = rootConfigService.getRootById(rootId);
                if (root != null) {
                    extraInfo = "实际目录: " + root.getPath();
                    paramLog.append(" | ").append(extraInfo);
                }
            }
        }
        
        logger.info(paramLog.toString());
        return extraInfo;
    }
    
    private void logMethodResult(String className, String methodName, long duration, 
                                  Object result, Throwable exception, String extraInfo) {
        StringBuilder resultLog = new StringBuilder();
        
        if (exception != null) {
            resultLog.append("[").append(className).append(".").append(methodName).append("] ");
            resultLog.append("失败 | 耗时: ").append(duration).append("ms");
            resultLog.append(" | 异常: ").append(exception.getClass().getSimpleName());
            logger.error(resultLog.toString());
        } else {
            resultLog.append("[").append(className).append(".").append(methodName).append("] ");
            resultLog.append("成功 | 耗时: ");
            
            if (duration < 100) {
                resultLog.append(duration).append("ms (快速)");
            } else if (duration < 500) {
                resultLog.append(duration).append("ms (正常)");
            } else if (duration < 2000) {
                resultLog.append(duration).append("ms (较慢)");
            } else {
                resultLog.append(duration).append("ms (很慢)");
            }
            
            if (result != null) {
                String resultInfo = formatResultInfo(result, className, methodName);
                if (resultInfo != null && !resultInfo.isEmpty()) {
                    resultLog.append(" | ").append(resultInfo);
                }
            }
            
            logger.info(resultLog.toString());
        }
    }
    
    private String formatResultInfo(Object result, String className, String methodName) {
        String resultType = result.getClass().getSimpleName();
        
        if (resultType.equals("ResponseEntity")) {
            try {
                ResponseEntity<?> response = (ResponseEntity<?>) result;
                Object body = response.getBody();
                String bodyInfo = body != null ? formatReturnValue(body) : "null";
                return String.format("ResponseEntity{status=%s, body=%s}", 
                                   response.getStatusCode(), bodyInfo);
            } catch (Exception e) {
                return "ResponseEntity";
            }
        }
        
        if (result instanceof byte[]) {
            return String.format("byte[%s]", formatFileSize(((byte[]) result).length));
        }
        
        if ("HashMap".equals(resultType) && "FileBrowseService".equals(className) && "browse".equals(methodName)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> browseResult = (Map<String, Object>) result;
                return formatBrowseResult(browseResult);
            } catch (Exception e) {
                return formatReturnValue(result);
            }
        }
        
        return formatReturnValue(result);
    }
    
    private String formatReturnValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map) return JSON.toJSONString(value);
        if (value instanceof List) return JSON.toJSONString(value);
        if (value instanceof String) return value.toString();
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        
        if (value instanceof File) {
            File file = (File) value;
            return String.format("File{%s, %s}", file.getName(), formatFileSize(file.length()));
        }
        
        String className = value.getClass().getSimpleName();
        String toString = value.toString();
        
        if (!toString.matches(".*@[0-9a-f]+$")) {
            if (toString.length() > 100) {
                return className + "{" + toString.substring(0, 97) + "...}";
            }
            return className + "{" + toString + "}";
        }
        
        return className;
    }
    
    @SuppressWarnings("unchecked")
    private String formatBrowseResult(Map<String, Object> result) {
        try {
            String currentPath = (String) result.get("currentPath");
            List<FileSystemItem> items = (List<FileSystemItem>) result.get("items");
            
            if (items == null) {
                return String.format("返回: 当前路径=%s | items=null", currentPath);
            }
            
            long folderCount = items.stream()
                .filter(item -> "folder".equals(item.getType()))
                .count();
            long fileCount = items.size() - folderCount;
            
            return String.format("返回: %d个文件夹 + %d个文件 (共%d项) | 当前路径: %s", 
                folderCount, fileCount, items.size(), currentPath);
        } catch (Exception e) {
            return "返回类型: Map (解析失败)";
        }
    }
    
    private String getParameterName(Parameter param) {
        if (param.isAnnotationPresent(PathVariable.class)) {
            PathVariable annotation = param.getAnnotation(PathVariable.class);
            String value = annotation.value();
            return value.isEmpty() ? param.getName() : value;
        }
        if (param.isAnnotationPresent(RequestParam.class)) {
            RequestParam annotation = param.getAnnotation(RequestParam.class);
            String value = annotation.value();
            return value.isEmpty() ? param.getName() : value;
        }
        return param.getName();
    }
    
    private String formatParameterValue(Object arg) {
        if (arg == null) return "null";
        
        if (arg instanceof MultipartFile) {
            MultipartFile file = (MultipartFile) arg;
            return String.format("MultipartFile{name='%s', size=%s, contentType='%s'}", 
                                file.getOriginalFilename(), formatFileSize(file.getSize()), file.getContentType());
        }
        
        if (arg instanceof File) {
            File file = (File) arg;
            return String.format("File{path='%s', size=%s, exists=%s}", 
                                file.getAbsolutePath(), formatFileSize(file.length()), file.exists());
        }
        
        if (arg instanceof byte[]) {
            return String.format("byte[%s]", formatFileSize(((byte[]) arg).length));
        }
        
        if (arg.getClass().isArray()) {
            return arg.getClass().getSimpleName() + "[" + java.lang.reflect.Array.getLength(arg) + "]";
        }
        
        if (arg instanceof String) {
            String str = (String) arg;
            if (str.length() > 100) return "'" + str.substring(0, 97) + "...'";
            return "'" + str + "'";
        }
        
        if (arg instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) arg;
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count > 0) sb.append(", ");
                if (count >= 3) {
                    sb.append("...(").append(map.size()).append(" total)");
                    break;
                }
                sb.append(entry.getKey()).append("=").append(formatSimpleValue(entry.getValue()));
                count++;
            }
            sb.append("}");
            return sb.toString();
        }
        
        if (arg instanceof List) {
            List<?> list = (List<?>) arg;
            if (list.isEmpty()) return "[]";
            return "[" + list.size() + " items]";
        }
        
        if (arg instanceof Number || arg instanceof Boolean) return arg.toString();
        
        String className = arg.getClass().getSimpleName();
        String toString = arg.toString();
        if (!toString.matches(".*@[0-9a-f]+$")) {
            if (toString.length() > 100) return className + "{" + toString.substring(0, 97) + "...}";
            return className + "{" + toString + "}";
        }
        return className;
    }
    
    private String formatSimpleValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > 30) return "'" + str.substring(0, 27) + "...'";
            return "'" + str + "'";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return value.getClass().getSimpleName();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
