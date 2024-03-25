package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.AllocationFailureRequest;
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
public class AllocationFailureAction extends AbstractActionSupport {
    private final BeerOrderRepository beerOrderRepository;
    private final JmsTemplate jmsTemplate;

    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        log.debug("ALLOCATION ORDER FAILURE ACTION | START");
        String orderId = Optional.ofNullable((String)stateContext.getMessage().getHeaders().get(BeerOrderManager.ORDER_ID_HEADER))
                        .orElseThrow( () -> new IllegalArgumentException("No Order ID header found on message context"));
        log.debug("ALLOCATION ORDER FAILURE ACTION | Retrieving BeerOrderId {}", orderId);
        beerOrderRepository.findById(UUID.fromString(orderId))
                .ifPresentOrElse(beerOrder -> {
                            AllocationFailureRequest request = AllocationFailureRequest.builder().orderId(beerOrder.getId()).build();
                            log.debug("ALLOCATION ORDER FAILURE ACTION | On StateMachine Complete for BeerOrderId {} | Sending request {}", orderId, request);
                            jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_FAILURE_QUEUE, request);
                            log.debug("ALLOCATION ORDER FAILURE ACTION | On StateMachine Complete for BeerOrderId {} | Request Sent", orderId);
                        }, () -> log.error("Order Not Found. Id {} ", orderId));



        log.debug("ALLOCATION ORDER FAILURE ACTION | END");
    }
}
