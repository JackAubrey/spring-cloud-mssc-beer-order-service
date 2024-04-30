package guru.sfg.beer.order.service.services.listeners;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.ValidateBeerOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class ValidationResultListener {
    private final BeerOrderManager beerOrderManager;

    @JmsListener(destination = JmsConfig.VALIDATE_ORDER_RESPONSE_QUEUE)
    void listen(Message<ValidateBeerOrderResult> message) {
        log.debug("Received Message {}", message);
        final ValidateBeerOrderResult result = message.getPayload();
        final UUID beerOrderId = result.getOrderId();

        log.info("Received Validation Order Result {}", result);
        beerOrderManager.processValidationResult(beerOrderId, result.getIsValid());
    }
}
