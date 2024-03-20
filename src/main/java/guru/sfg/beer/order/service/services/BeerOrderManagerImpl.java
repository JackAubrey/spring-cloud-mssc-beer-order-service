package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateChangeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class BeerOrderManagerImpl implements BeerOrderManager {
    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineFactory;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderStateChangeInterceptor interceptor;

    @Override
    @Transactional
    public BeerOrder newBeerOrder(BeerOrder beerOrder) {
        log.info("Saving new BeerOrder");
        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);
        BeerOrder savedOrder = beerOrderRepository.save(beerOrder);
        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATE_ORDER);
        return savedOrder;
    }

    @Override
    @Transactional
    public void processValidationResult(UUID orderId, Boolean isValid) {
        BeerOrder beerOrder = beerOrderRepository.getReferenceById(orderId);

        if( Boolean.TRUE.equals(isValid) ) {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_SUCCESS);
        } else {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_FAILED);
        }
    }

    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum beerOrderEvent) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);
        sendEvent(beerOrder, sm, beerOrderEvent);
    }

    private void sendEvent(BeerOrder beerOrder, StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm, BeerOrderEventEnum event) {
        log.debug("Sending StateMachine Event | BeerOrder ID {} | Event {}", beerOrder.getId(), event);
        Message<BeerOrderEventEnum> msg = MessageBuilder.withPayload(event)
                .setHeader(ORDER_ID_HEADER, beerOrder.getId().toString())
                .build();

        sm.sendEvent(Mono.just(msg)).subscribe();
    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine = stateMachineFactory.getStateMachine(beerOrder.getId().toString());
        stateMachine.stopReactively().subscribe();
        stateMachine.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.addStateMachineInterceptor(interceptor);
            sma.resetStateMachineReactively(new DefaultStateMachineContext<>(beerOrder.getOrderStatus(), null, null, null)).subscribe();
        });

        stateMachine.startReactively().subscribe();

        return stateMachine;
    }
}
