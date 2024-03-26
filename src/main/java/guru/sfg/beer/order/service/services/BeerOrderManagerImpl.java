package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateChangeInterceptor;
import guru.sfg.brewery.model.BeerOrderDto;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class BeerOrderManagerImpl implements BeerOrderManager {
    public static final String ORDER_NOT_FOUND_ID = "Order Not Found. Id: {}";
    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineFactory;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderStateChangeInterceptor interceptor;

    @Transactional
    @Override
    public BeerOrder newBeerOrder(BeerOrder beerOrder) {
        log.info("Saving new BeerOrder");
        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

        BeerOrder savedBeerOrder = beerOrderRepository.saveAndFlush(beerOrder);
        sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATE_ORDER);
        return savedBeerOrder;
    }

    @Transactional
    @Override
    public void processValidationResult(UUID beerOrderId, Boolean isValid) {
        log.debug("Process Validation Result for beerOrderId: {}", beerOrderId + " Valid? " + isValid);

        beerOrderRepository.findById(beerOrderId)
                .ifPresentOrElse(beerOrder -> {
                    if( Boolean.TRUE.equals(isValid) ) {
                        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_SUCCESS);

                        //wait for status change
                        awaitForStatus(beerOrderId, BeerOrderStatusEnum.VALIDATED);

                        beerOrderRepository.findById(beerOrderId)
                                .ifPresentOrElse(validatedOrder -> sendBeerOrderEvent(validatedOrder, BeerOrderEventEnum.ALLOCATE_ORDER),
                                        () -> log.error("Process Validation Result | Send BeerOrder Event | "+ORDER_NOT_FOUND_ID, beerOrderId));
                    } else {
                        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_FAILED);
                    }
                }, () -> log.error("Process Validation Result | "+ORDER_NOT_FOUND_ID, beerOrderId));
    }

    @Override
    public void beerOrderAllocationPassed(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_SUCCESS);
            awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.ALLOCATED);
            updateAllocatedQty(beerOrderDto);
        }, () -> log.error("BeerOrder Allocation Passed | " + ORDER_NOT_FOUND_ID, beerOrderDto.getId() ));
    }

    @Override
    public void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
            awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.PENDING_INVENTORY);
            updateAllocatedQty(beerOrderDto);
        }, () -> log.error("BeerOrder Allocation PendingInventory | "+ORDER_NOT_FOUND_ID, beerOrderDto.getId() ));

    }

    private void updateAllocatedQty(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> allocatedOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        allocatedOrderOptional.ifPresentOrElse(allocatedOrder -> {
            allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
                if(beerOrderLine.getId() .equals(beerOrderLineDto.getId())){
                    beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
                }
            }));

            beerOrderRepository.saveAndFlush(allocatedOrder);
        }, () -> log.error("Update AllocatedQty | "+ORDER_NOT_FOUND_ID, beerOrderDto.getId()));
    }

    @Override
    public void beerOrderAllocationFailed(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder ->
                        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_FAILED),
                () -> log.error("BeerOrder Allocation Failed | " + ORDER_NOT_FOUND_ID, beerOrderDto.getId()) );

    }

    @Override
    public void beerOrderPickedUp(UUID id) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(id);

        beerOrderOptional.ifPresentOrElse(beerOrder ->
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.BEER_ORDER_PICKED_UP),
                () -> log.error("BeerOrder PickedUp | " + ORDER_NOT_FOUND_ID, id));
    }

    @Override
    public void cancelOrder(UUID id) {
        beerOrderRepository.findById(id).ifPresentOrElse(beerOrder ->
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.CANCEL_ORDER),
            () -> log.error("Cancel Order | " + ORDER_NOT_FOUND_ID, id));
    }

    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum beerOrderEvent) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);
        sendEvent(beerOrder, sm, beerOrderEvent);
    }

    private void awaitForStatus(UUID beerOrderId, BeerOrderStatusEnum statusEnum) {

        AtomicBoolean found = new AtomicBoolean(false);
        AtomicInteger loopCount = new AtomicInteger(0);
        log.debug("START Awaiting Order {} with Status {}", beerOrderId, statusEnum);

        while (!found.get()) {
            if (loopCount.incrementAndGet() > 10) {
                found.set(true);
                log.debug("Loop Retries exceeded");
            }

            beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
                if (beerOrder.getOrderStatus().equals(statusEnum)) {
                    found.set(true);
                    log.debug("Order Found");
                } else {
                    log.debug("Order Status Not Equal. Expected: " + statusEnum.name() + " Found: " + beerOrder.getOrderStatus().name());
                }
            }, () -> log.debug("Order Id Not Found"));

            if (!found.get()) {
                try {
                    log.debug("Sleeping for retry");
                    Thread.sleep(100);
                } catch (Exception e) {
                    // do nothing
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.debug("END Awaiting Order {} with Status {}", beerOrderId, statusEnum);
    }

    private void sendEvent(BeerOrder beerOrder, StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm, BeerOrderEventEnum event) {
        log.debug("Sending StateMachine Event | BeerOrder ID {} | Event {}", beerOrder.getId(), event);
        Message<BeerOrderEventEnum> msg = MessageBuilder.withPayload(event)
                .setHeader(ORDER_ID_HEADER, beerOrder.getId().toString())
                .build();

        sm.sendEvent(Mono.just(msg)).subscribe(c -> log.debug("State Machine SendEvent Consume"), e -> log.error("State Machine SendEvent Consume Error:", e));
    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine = stateMachineFactory.getStateMachine(beerOrder.getId().toString());
        stateMachine.stopReactively().subscribe(c -> log.debug("State Machine Build Consume"), e -> log.error("State Machine Build Consume Error:", e));
        stateMachine.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.addStateMachineInterceptor(interceptor);
            sma.resetStateMachineReactively(new DefaultStateMachineContext<>(beerOrder.getOrderStatus(), null, null, null))
                    .subscribe(c -> log.debug("State Machine Build ResetState"), e -> log.error("State Machine Build ResetState Error:", e));
        });

        stateMachine.startReactively().subscribe(c -> log.debug("State Machine Build Consume"), e -> log.error("State Machine Build Consume Error:", e));

        return stateMachine;
    }
}
