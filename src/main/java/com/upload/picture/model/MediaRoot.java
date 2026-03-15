package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 媒体根目录配置模型
 */
public class MediaRoot {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("icon")
    private String icon;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("readonly")
    private boolean readonly;
    
    private transient Integer itemCount;
    private transient Long totalSize;
    private transient Long lastModified;
    
    public MediaRoot() {
    }
    
    public MediaRoot(String id, String name, String path) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.icon = "folder.fill";
        this.readonly = false;
    }
    
    public MediaRoot(String id, String name, String path, String icon, String description, boolean readonly) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.description = description;
        this.readonly = readonly;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isReadonly() { return readonly; }
    public void setReadonly(boolean readonly) { this.readonly = readonly; }
    
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
    
    public Long getLastModified() { return lastModified; }
    public void setLastModified(Long lastModified) { this.lastModified = lastModified; }
}
