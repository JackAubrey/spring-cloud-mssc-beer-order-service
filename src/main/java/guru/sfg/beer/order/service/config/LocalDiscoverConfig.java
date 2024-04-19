package guru.sfg.beer.order.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Profile("cloud-local")
@EnableDiscoveryClient
@Configuration
public class LocalDiscoverConfig {
    public LocalDiscoverConfig() {
        log.info("################## Local Discovery U&R");
    }
}
