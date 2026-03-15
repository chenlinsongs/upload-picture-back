package com.upload.picture.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 图片完整元数据模型
 * 与 iOS 客户端的 ImageMetadataHelper.CompleteMetadata 结构对应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageMetadata {
    
    private Long fileSize;
    private String creationDate;
    
    private Integer pixelWidth;
    private Integer pixelHeight;
    private Integer orientation;
    
    private String dateTimeOriginal;
    private String dateTimeDigitized;
    
    private Double gpsLatitude;
    private Double gpsLongitude;
    private Double gpsAltitude;
    
    private String make;
    private String model;
    private String lensMake;
    private String lensModel;
    
    private Double fNumber;
    private String exposureTime;
    private Integer iso;
    private Double focalLength;
    private String flash;
    
    private String colorSpace;
    private String profileName;
    private Integer dpiWidth;
    private Integer dpiHeight;
    
    private String software;
    
    private Map<String, String> exifData;
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }
    
    public Integer getPixelWidth() { return pixelWidth; }
    public void setPixelWidth(Integer pixelWidth) { this.pixelWidth = pixelWidth; }
    
    public Integer getPixelHeight() { return pixelHeight; }
    public void setPixelHeight(Integer pixelHeight) { this.pixelHeight = pixelHeight; }
    
    public Integer getOrientation() { return orientation; }
    public void setOrientation(Integer orientation) { this.orientation = orientation; }
    
    public String getDateTimeOriginal() { return dateTimeOriginal; }
    public void setDateTimeOriginal(String dateTimeOriginal) { this.dateTimeOriginal = dateTimeOriginal; }
    
    public String getDateTimeDigitized() { return dateTimeDigitized; }
    public void setDateTimeDigitized(String dateTimeDigitized) { this.dateTimeDigitized = dateTimeDigitized; }
    
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
    
    public String getLensMake() { return lensMake; }
    public void setLensMake(String lensMake) { this.lensMake = lensMake; }
    
    public String getLensModel() { return lensModel; }
    public void setLensModel(String lensModel) { this.lensModel = lensModel; }
    
    public Double getfNumber() { return fNumber; }
    public void setfNumber(Double fNumber) { this.fNumber = fNumber; }
    
    public String getExposureTime() { return exposureTime; }
    public void setExposureTime(String exposureTime) { this.exposureTime = exposureTime; }
    
    public Integer getIso() { return iso; }
    public void setIso(Integer iso) { this.iso = iso; }
    
    public Double getFocalLength() { return focalLength; }
    public void setFocalLength(Double focalLength) { this.focalLength = focalLength; }
    
    public String getFlash() { return flash; }
    public void setFlash(String flash) { this.flash = flash; }
    
    public String getColorSpace() { return colorSpace; }
    public void setColorSpace(String colorSpace) { this.colorSpace = colorSpace; }
    
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    
    public Integer getDpiWidth() { return dpiWidth; }
    public void setDpiWidth(Integer dpiWidth) { this.dpiWidth = dpiWidth; }
    
    public Integer getDpiHeight() { return dpiHeight; }
    public void setDpiHeight(Integer dpiHeight) { this.dpiHeight = dpiHeight; }
    
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    
    public Map<String, String> getExifData() { return exifData; }
    public void setExifData(Map<String, String> exifData) { this.exifData = exifData; }
}
