package guru.sfg.beer.order.service.sm;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderStateChangeInterceptor extends StateMachineInterceptorAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {
    private final BeerOrderRepository beerOrderRepository;

    @Override
    public void preStateChange(State<BeerOrderStatusEnum, BeerOrderEventEnum> state,
                               Message<BeerOrderEventEnum> message,
                               Transition<BeerOrderStatusEnum, BeerOrderEventEnum> transition,
                               StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine,
                               StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> rootStateMachine) {
        Optional.ofNullable(message)
                .flatMap(msg -> Optional.ofNullable((String)message.getHeaders().getOrDefault(BeerOrderManager.ORDER_ID_HEADER, null)) )
                        .ifPresent(orderId -> {
                            log.debug("Saving state for BeerOrder ID {} and Status {}", orderId, state.getId());
                            beerOrderRepository.findById(UUID.fromString(orderId))
                                    .ifPresentOrElse(beerOrder -> {
                                        beerOrder.setOrderStatus(state.getId());
                                        BeerOrder saveOrder = beerOrderRepository.saveAndFlush(beerOrder);
                                        log.debug("Saved and Flushed beer-order {} with state {}", saveOrder.getId(), state.getId());

                                        findWithRetry(saveOrder.getId()).ifPresentOrElse(b -> log.info("OK saved order {}", b.getId()),
                                                () -> log.error("Unable to read the saved order {}", saveOrder.getId()));
                                    }, () -> log.error("Unable to load BeerOrder by Id {}", orderId));

                        });
    }

    private Optional<BeerOrder> findWithRetry(UUID beerOrderId) {
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicInteger loopCount = new AtomicInteger(0);
        Optional<BeerOrder> optionalBeerOrder = Optional.empty();
        log.debug("START find Order {}", beerOrderId);

        while (!found.get()) {
            if (loopCount.incrementAndGet() > 10) {
                found.set(true);
                log.debug("Find Order | Loop Retries exceeded for OrderId {}", beerOrderId);
            } else {
                optionalBeerOrder = beerOrderRepository.findById(beerOrderId);
                found.set(optionalBeerOrder.isPresent());

                if (!found.get()) {
                    try {
                        log.debug("Find Order | Sleeping for retry");
                        Thread.sleep(100);
                    } catch (Exception e) {
                        // do nothing
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return optionalBeerOrder;
    }
}
