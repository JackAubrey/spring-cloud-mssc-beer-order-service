package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;

public interface BeerOrderManager {
    String ORDER_ID_HEADER = "ORDER_ID_HEADER";

    BeerOrder newBeerOrder(BeerOrder beerOrder);
}
