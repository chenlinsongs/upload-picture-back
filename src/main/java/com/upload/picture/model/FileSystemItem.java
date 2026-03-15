package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件系统项（文件夹或文件）
 */
public class FileSystemItem {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("modifiedDate")
    private Long modifiedDate;
    
    @JsonProperty("itemCount")
    private Integer itemCount;
    
    @JsonProperty("totalSize")
    private Long totalSize;
    
    @JsonProperty("fileSize")
    private Long fileSize;
    
    @JsonProperty("thumbnailUrl")
    private String thumbnailUrl;
    
    @JsonProperty("fullUrl")
    private String fullUrl;
    
    @JsonProperty("width")
    private Integer width;
    
    @JsonProperty("height")
    private Integer height;
    
    @JsonProperty("duration")
    private Double duration;
    
    @JsonProperty("creationDate")
    private Long creationDate;
    
    @JsonProperty("hasEXIF")
    private Boolean hasEXIF;
    
    @JsonProperty("videoUrl")
    private String videoUrl;
    
    @JsonProperty("imageFileName")
    private String imageFileName;
    
    @JsonProperty("videoFileName")
    private String videoFileName;
    
    public FileSystemItem() {
    }
    
    public FileSystemItem(String id, String type, String name, String path) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.path = path;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    public Long getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Long modifiedDate) { this.modifiedDate = modifiedDate; }
    
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public String getFullUrl() { return fullUrl; }
    public void setFullUrl(String fullUrl) { this.fullUrl = fullUrl; }
    
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    
    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }
    
    public Long getCreationDate() { return creationDate; }
    public void setCreationDate(Long creationDate) { this.creationDate = creationDate; }
    
    public Boolean getHasEXIF() { return hasEXIF; }
    public void setHasEXIF(Boolean hasEXIF) { this.hasEXIF = hasEXIF; }
    
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    
    public String getImageFileName() { return imageFileName; }
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }
    
    public String getVideoFileName() { return videoFileName; }
    public void setVideoFileName(String videoFileName) { this.videoFileName = videoFileName; }
}
