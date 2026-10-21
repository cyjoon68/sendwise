package com.sendwise.carriermock.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendwise.carriermock.domain.AcceptSend;
import com.sendwise.carriermock.domain.CarrierCodeBook;
import com.sendwise.carriermock.domain.CarrierRuntime;
import com.sendwise.carriermock.domain.ResultSink;
import com.sendwise.carriermock.domain.TemplateBook;
import com.sendwise.carriermock.domain.TpsGate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MockProperties.class)
public class MockConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer omitNulls() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public CodesLoader codesLoader(ObjectMapper objectMapper) {
        return new CodesLoader(objectMapper);
    }

    @Bean
    public CarrierCodeBook carrierCodeBook(MockProperties properties, CodesLoader codesLoader) {
        return CarrierCodeBook.from(codesLoader.load(properties.getCodesPath()));
    }

    @Bean
    public TemplateBook templateBook(MockProperties properties) {
        Map<String, String> bodies = new LinkedHashMap<>();
        for (MockProperties.TemplateProps template : properties.getTemplates()) {
            if (template.getCode() == null || template.getCode().isBlank()
                    || template.getBody() == null || template.getBody().isBlank()) {
                throw new IllegalArgumentException("INVALID_TEMPLATE");
            }
            if (bodies.put(template.getCode(), template.getBody()) != null) {
                throw new IllegalArgumentException("DUPLICATE_TEMPLATE_" + template.getCode());
            }
        }
        return new TemplateBook(bodies);
    }

    @Bean
    public CarrierRegistry carrierRegistry(MockProperties properties) {
        return CarrierRegistry.from(properties);
    }

    @Bean
    public TpsGate tpsGate() {
        return new TpsGate();
    }

    @Bean(destroyMethod = "close")
    public HttpResultSink httpResultSink(MockProperties properties) {
        return new HttpResultSink(properties.getCallbackBaseUrl());
    }

    @Bean
    public AcceptSend acceptSend(
            TpsGate tpsGate,
            CarrierRuntime runtime,
            TemplateBook templates,
            CarrierCodeBook codes,
            ResultSink sink) {
        return new AcceptSend(tpsGate, runtime, templates, codes, sink);
    }
}
