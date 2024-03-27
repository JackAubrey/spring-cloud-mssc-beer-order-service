package guru.sfg.beer.order.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;

@Slf4j
@ConditionalOnProperty(name = "config.eureka.discovery.client", havingValue = "local-discovery", matchIfMissing = false)
@EnableDiscoveryClient
@Configuration
public class LocalDiscoverConfig {
    public LocalDiscoverConfig() {
        log.info("################## Local Discovery U&R");
    }
}
