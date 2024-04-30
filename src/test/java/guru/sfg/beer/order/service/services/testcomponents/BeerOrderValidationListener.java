package guru.sfg.beer.order.service.services.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateBeerOrderResult;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
import guru.sfg.brewery.model.events.ValidateBeerOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderValidationListener {
    private final JmsTemplate jmsTemplate;
    @JmsListener(destination = JmsConfig.VALIDATE_ORDER_QUEUE)
    void listen(Message<ValidateBeerOrderRequest> message) {
        log.info("Received Validation Order Request Message: {}", message);
        ValidateBeerOrderRequest request = message.getPayload();
        boolean isValid = !TestConstants.FAIL_VALIDATION.equals(request.getBeerOrderDto().getCustomerRef());
        boolean sendResponse = true;

        if(TestConstants.DO_NOT_VALIDATE.equals(request.getBeerOrderDto().getCustomerRef())) {
            sendResponse = false;
        }

        if(sendResponse) {
            jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_RESPONSE_QUEUE,
                    ValidateBeerOrderResult.builder()
                            .isValid(isValid)
                            .orderId(request.getBeerOrderDto().getId())
                            .build());
        }
    }
}
