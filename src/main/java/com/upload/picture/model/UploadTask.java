package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

/**
 * 分片上传任务模型
 * 用于记录断点续传的上传状态
 */
public class UploadTask {
    
    private String uploadId;
    private String assetId;
    private String fileName;
    private long fileSize;
    private String contentType;
    private long chunkSize;
    private int totalChunks;
    private Set<Integer> uploadedChunks;
    private long createdAt;
    private long expiresAt;
    private String fileType;
    private String metadataJson;
    private String md5;
    private String targetFolder;
    private String status;
    
    public UploadTask() {
        this.uploadedChunks = new HashSet<>();
    }
    
    public UploadTask(String uploadId, String assetId, String fileName, long fileSize, 
                      String contentType, long chunkSize, int totalChunks) {
        this.uploadId = uploadId;
        this.assetId = assetId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.uploadedChunks = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + 24 * 60 * 60 * 1000L;
    }
    
    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
    
    @JsonIgnore
    public boolean isComplete() {
        return uploadedChunks.size() >= totalChunks;
    }
    
    @JsonIgnore
    public double getProgress() {
        if (totalChunks <= 0) return 0.0;
        return (double) uploadedChunks.size() / totalChunks;
    }
    
    public void addUploadedChunk(int chunkIndex) {
        uploadedChunks.add(chunkIndex);
    }
    
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public long getChunkSize() { return chunkSize; }
    public void setChunkSize(long chunkSize) { this.chunkSize = chunkSize; }
    
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    
    public Set<Integer> getUploadedChunks() { return uploadedChunks; }
    public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    
    public String getTargetFolder() { return targetFolder; }
    public void setTargetFolder(String targetFolder) { this.targetFolder = targetFolder; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @Override
    public String toString() {
        return "UploadTask{" +
                "uploadId='" + uploadId + '\'' +
                ", assetId='" + assetId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", totalChunks=" + totalChunks +
                ", uploadedChunks=" + uploadedChunks.size() +
                ", progress=" + String.format("%.1f%%", getProgress() * 100) +
                ", expired=" + isExpired() +
                '}';
    }
}
