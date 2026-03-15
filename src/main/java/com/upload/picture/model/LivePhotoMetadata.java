package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Live Photo 完整元数据模型
 * 与 iOS 客户端的 LivePhotoCompleteMetadata 结构对应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LivePhotoMetadata {
    
    private ImageMetadata imageMetadata;
    private VideoMetadata videoMetadata;
    
    public LivePhotoMetadata() {
    }
    
    public LivePhotoMetadata(ImageMetadata imageMetadata, VideoMetadata videoMetadata) {
        this.imageMetadata = imageMetadata;
        this.videoMetadata = videoMetadata;
    }
    
    public ImageMetadata getImageMetadata() { return imageMetadata; }
    public void setImageMetadata(ImageMetadata imageMetadata) { this.imageMetadata = imageMetadata; }
    
    public VideoMetadata getVideoMetadata() { return videoMetadata; }
    public void setVideoMetadata(VideoMetadata videoMetadata) { this.videoMetadata = videoMetadata; }
}
