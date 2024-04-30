package guru.sfg.beer.order.service.services.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateBeerOrderRequest;
import guru.sfg.brewery.model.events.AllocateBeerOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {
    private final JmsTemplate jmsTemplate;
    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    void listen(AllocateBeerOrderRequest request) {
        log.info("Received Allocation Order Request: {}", request);

        boolean pendingInventory = TestConstants.PARTIAL_ALLOCATION.equals(request.getBeerOrderDto().getCustomerRef());
        boolean allocationError = TestConstants.FAIL_ALLOCATION.equals(request.getBeerOrderDto().getCustomerRef());
        boolean sendResponse = true;

        if( TestConstants.DO_NOT_ALLOCATE.equals(request.getBeerOrderDto().getCustomerRef()) ) {
            sendResponse = false;
        }

        request.getBeerOrderDto().getBeerOrderLines().forEach(beerOrderLineDto -> {
            if (pendingInventory) {
                beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity() -1);
            } else {
                beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
            }
        });

        if(sendResponse) {
            jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE,
                    AllocateBeerOrderResult.builder()
                            .beerOrderDto(request.getBeerOrderDto())
                            .pendingInventory(pendingInventory)
                            .allocationError(allocationError)
                            .build());
        }
    }
}
