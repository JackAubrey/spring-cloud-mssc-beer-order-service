package guru.sfg.beer.order.service.config;

import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BraveConfig {
    public BraveConfig() {
        log.debug("---> Brave Config");
    }

    @Bean
    CorrelationScopeCustomizer correlationScopeCustomizer() {
        return builder -> builder
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageFields.PARENT_ID).name("parentId").build())
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageFields.SAMPLED).name("sample").build());
    }
}
