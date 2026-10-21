package com.sendwise.api.config;

import org.apache.catalina.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Bean
    Clock clock(@Value("${sendwise.fixed-now:}") String fixed) {
        if (fixed == null || fixed.isBlank()) {
            return Clock.system(ZoneId.of("Asia/Seoul"));
        }
        return Clock.fixed(Instant.parse(fixed), ZoneId.of("Asia/Seoul"));
    }

    @Bean
    TomcatServletWebServerFactory tomcat() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addContextCustomizers(this::useWebappDir);
        return factory;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    private void useWebappDir(Context context) {
        Path webapp = Path.of("src/main/webapp");
        if (Files.isDirectory(webapp)) {
            context.setDocBase(webapp.toAbsolutePath().toString());
        }
    }
}
