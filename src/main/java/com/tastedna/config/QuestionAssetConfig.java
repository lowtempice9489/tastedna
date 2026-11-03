package com.tastedna.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

// 원본 이미지를 복제하지 않고 로컬 질문 화면에서 제공한다.
@Configuration
@Profile("local")
public class QuestionAssetConfig implements WebMvcConfigurer {
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String root = Path.of("gender").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/assets/gender/**")
                .addResourceLocations(root.endsWith("/") ? root : root + "/");
    }
}

