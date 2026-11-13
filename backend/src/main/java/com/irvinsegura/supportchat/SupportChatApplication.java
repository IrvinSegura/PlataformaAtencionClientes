package com.irvinsegura.supportchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
public class SupportChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportChatApplication.class, args);
    }
}
