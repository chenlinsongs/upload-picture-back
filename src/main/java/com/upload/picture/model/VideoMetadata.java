package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 视频完整元数据模型
 * 与 iOS 客户端的 CompleteMetadata 结构对应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoMetadata {
    
    private Double duration;
    private Long fileSize;
    private String creationDate;
    
    private Integer videoWidth;
    private Integer videoHeight;
    private String videoCodec;
    private Integer videoBitrate;
    private Double frameRate;
    
    private String audioCodec;
    private Integer audioBitrate;
    private Integer audioChannels;
    private Double audioSampleRate;
    
    private Double gpsLatitude;
    private Double gpsLongitude;
    private Double gpsAltitude;
    
    private String make;
    private String model;
    private String software;
    
    private Map<String, String> metadata;
    
    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }
    
    public Integer getVideoWidth() { return videoWidth; }
    public void setVideoWidth(Integer videoWidth) { this.videoWidth = videoWidth; }
    
    public Integer getVideoHeight() { return videoHeight; }
    public void setVideoHeight(Integer videoHeight) { this.videoHeight = videoHeight; }
    
    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    
    public Integer getVideoBitrate() { return videoBitrate; }
    public void setVideoBitrate(Integer videoBitrate) { this.videoBitrate = videoBitrate; }
    
    public Double getFrameRate() { return frameRate; }
    public void setFrameRate(Double frameRate) { this.frameRate = frameRate; }
    
    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    
    public Integer getAudioBitrate() { return audioBitrate; }
    public void setAudioBitrate(Integer audioBitrate) { this.audioBitrate = audioBitrate; }
    
    public Integer getAudioChannels() { return audioChannels; }
    public void setAudioChannels(Integer audioChannels) { this.audioChannels = audioChannels; }
    
    public Double getAudioSampleRate() { return audioSampleRate; }
    public void setAudioSampleRate(Double audioSampleRate) { this.audioSampleRate = audioSampleRate; }
    
    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }
    
    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }
    
    public Double getGpsAltitude() { return gpsAltitude; }
    public void setGpsAltitude(Double gpsAltitude) { this.gpsAltitude = gpsAltitude; }
    
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
