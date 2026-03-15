package com.upload.picture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UploadPictureApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadPictureApplication.class, args);
    }
}
