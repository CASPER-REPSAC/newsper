package com.example.newsper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String homePath = "/home/casper/";
        registry.addResourceHandler("/profile/**")
                .addResourceLocations("file://" + homePath + "profile/")
                .resourceChain(true)
                .addResolver(new CustomPathResourceResolver());
        registry.addResourceHandler("/article/**")
                .addResourceLocations("file://" + homePath + "article/")
                .resourceChain(true)
                .addResolver(new CustomPathResourceResolver());
        registry.addResourceHandler("/assignment/**")
                .addResourceLocations("file://" + homePath + "assignment/")
                .resourceChain(true)
                .addResolver(new CustomPathResourceResolver());
        registry.addResourceHandler("/submit/**")
                .addResourceLocations("file://" + homePath + "submit/")
                .resourceChain(true)
                .addResolver(new CustomPathResourceResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000", "https://www.casper.or.kr")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    static class CustomPathResourceResolver extends PathResourceResolver {
        @SuppressWarnings("NullableProblems")
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            resourcePath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
            return super.getResource(resourcePath, location);
        }
    }
}
