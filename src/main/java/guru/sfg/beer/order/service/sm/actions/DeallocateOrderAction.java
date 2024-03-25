package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.events.DeallocateBeerOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeallocateOrderAction extends AbstractActionSupport {
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderMapper beerOrderMapper;
    private final JmsTemplate jmsTemplate;

    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        log.debug("DEALLOCATE ORDER ACTION | START");
        String orderId = Optional.ofNullable((String)stateContext.getMessage().getHeaders().get(BeerOrderManager.ORDER_ID_HEADER))
                        .orElseThrow( () -> new IllegalArgumentException("No Order ID header found on message context"));
        log.debug("DEALLOCATE ORDER ACTION | Retrieving BeerOrderId {}", orderId);
        beerOrderRepository.findById(UUID.fromString(orderId))
                .ifPresentOrElse(beerOrder -> {
                    DeallocateBeerOrderRequest request = DeallocateBeerOrderRequest.builder().beerOrderDto(beerOrderMapper.beerOrderToDto(beerOrder)).build();
                    log.debug("DEALLOCATE ORDER ACTION | On StateMachine Complete for BeerOrderId {} | Sending request {}", orderId, request);
                    jmsTemplate.convertAndSend(JmsConfig.DEALLOCATE_ORDER_QUEUE, request);
                    log.debug("DEALLOCATE ORDER ACTION | On StateMachine Complete for BeerOrderId {} | Request Sent", orderId);
                }, () -> log.error("Order Not Found. Id {} ", orderId));

        log.debug("DEALLOCATE ORDER ACTION | END");
    }
}
