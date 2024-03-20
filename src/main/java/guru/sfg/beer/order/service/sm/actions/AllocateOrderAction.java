package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.events.AllocateBeerOrderRequest;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
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
public class AllocateOrderAction extends AbstractActionSupport {
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderMapper beerOrderMapper;
    private final JmsTemplate jmsTemplate;

    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        log.debug("ALLOCATE ORDER ACTION | START");
        String orderId = Optional.ofNullable((String)stateContext.getMessage().getHeaders().get(BeerOrderManager.ORDER_ID_HEADER))
                        .orElseThrow( () -> new IllegalArgumentException("No Order ID header found on message context"));
        log.debug("ALLOCATE ORDER ACTION | Retrieving BeerOrderId {}", orderId);
        BeerOrder beerOrder = beerOrderRepository.getReferenceById(UUID.fromString(orderId));
        AllocateBeerOrderRequest request = AllocateBeerOrderRequest.builder().beerOrderDto(beerOrderMapper.beerOrderToDto(beerOrder)).build();
        log.debug("ALLOCATE ORDER ACTION | Sending StateMachine ALLOCATE_ORDER Event for BeerOrderId {}", orderId);
        sendEvent(stateContext, BeerOrderEventEnum.ALLOCATE_ORDER)
                .doOnComplete( () -> {
                    log.debug("ALLOCATE ORDER ACTION | On StateMachine Complete for BeerOrderId {} | Sending request {}", orderId, request);
                    jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_QUEUE, request);
                    log.debug("ALLOCATE ORDER ACTION | On StateMachine Complete for BeerOrderId {} | Request Sent", orderId);
                }).subscribe();
        log.debug("ALLOCATE ORDER ACTION | END");
    }
}
