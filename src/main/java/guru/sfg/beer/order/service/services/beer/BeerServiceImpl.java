package guru.sfg.beer.order.service.services.beer;

import guru.sfg.beer.order.service.services.beer.model.BeerDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

@Profile("!local-discovery")
@Slf4j
@ConfigurationProperties(prefix = "sfg.brewery", ignoreUnknownFields = true)
@Component
public class BeerServiceImpl implements BeerService {
    public static final String BEER_BY_ID_SERVICE_PATH = "/api/v1/beer/";
    public static final String BEER_BY_UPC_SERVICE_PATH = "/api/v1/beerUpc/";
    private final RestTemplate restTemplate;

    private String beerServiceHost;

    public BeerServiceImpl(RestTemplateBuilder restTemplateBuilder,
                           @Value("${sfg.brewery.beer-service-user}") String inventoryUser,
                           @Value("${sfg.brewery.beer-service-password}")String inventoryPassword) {
        this.restTemplate = restTemplateBuilder
                .basicAuthentication(inventoryUser, inventoryPassword)
                .build();
    }

    @Override
    public Optional<BeerDto> getBeerById(UUID beerId) {
        log.debug("Calling Beer Service by ID: {}", beerId);
        Assert.notNull(beerId, "The Beer Id must not be null");

        return Optional.ofNullable(
                restTemplate.getForObject(beerServiceHost + BEER_BY_ID_SERVICE_PATH + beerId, BeerDto.class)
        );
    }

    @Override
    public Optional<BeerDto> getBeerByUpc(String upc) {
        log.debug("Calling Beer Service by UPC: {}", upc);
        Assert.notNull(upc, "The Beer UPC must not be null");

        return Optional.ofNullable(
                restTemplate.getForObject(beerServiceHost + BEER_BY_UPC_SERVICE_PATH + upc, BeerDto.class)
        );
    }

    public void setBeerServiceHost(String beerServiceHost) {
        this.beerServiceHost = beerServiceHost;
    }
}
