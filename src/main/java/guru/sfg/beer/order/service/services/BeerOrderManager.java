package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;

import java.util.UUID;

public interface BeerOrderManager {
    String ORDER_ID_HEADER = "ORDER_ID_HEADER";

    BeerOrder newBeerOrder(BeerOrder beerOrder);

    void processValidationResult(UUID orderId, Boolean isValid);
}
