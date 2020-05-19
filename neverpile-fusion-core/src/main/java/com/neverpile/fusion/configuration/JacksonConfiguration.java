package com.neverpile.fusion.configuration;

import java.io.IOException;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@ComponentScan
public class JacksonConfiguration {
  // Method name designed to prevent clash with bean from eureka
  @Bean
  Jackson2ObjectMapperBuilderCustomizer fusionJacksonCustomizer() {
    return new Jackson2ObjectMapperBuilderCustomizer() {

      @Override
      public void customize(final Jackson2ObjectMapperBuilder b) {
        b.annotationIntrospector(new JacksonAnnotationIntrospector());
        b.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        b.dateFormat(new StdDateFormat());
        b.modules(new JavaTimeModule());

        b.serializerByType(MediaType.class, new JsonSerializer<MediaType>() {
          @Override
          public void serialize(final MediaType value, final JsonGenerator gen, final SerializerProvider serializers)
              throws IOException {
            gen.writeString(value.toString());
          }
        });

        b.deserializerByType(MediaType.class, new JsonDeserializer<MediaType>() {
          @Override
          public MediaType deserialize(final JsonParser p, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            String v = p.getValueAsString();
            return v != null ? MediaType.parseMediaType(v) : null;
          }
        });
      }
    };
  }
}
