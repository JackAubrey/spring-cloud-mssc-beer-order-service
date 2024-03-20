package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.action.Action;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

abstract class AbstractActionSupport implements Action<BeerOrderStatusEnum, BeerOrderEventEnum> {
    Flux<StateMachineEventResult<BeerOrderStatusEnum, BeerOrderEventEnum>> sendEvent(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> context, BeerOrderEventEnum event) {
        Mono<Message<BeerOrderEventEnum>> monoMessage = buildMessageAsMono(context, event);
        return context.getStateMachine()
                .sendEvent(monoMessage);
    }

    private Mono<Message<BeerOrderEventEnum>> buildMessageAsMono(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> context, BeerOrderEventEnum event) {
        return Mono.just(buildMessage(context, event));
    }

    private Message<BeerOrderEventEnum> buildMessage(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> context, BeerOrderEventEnum event) {
        return MessageBuilder
                .withPayload(event)
                .setHeader(BeerOrderManager.ORDER_ID_HEADER, context.getMessageHeader(BeerOrderManager.ORDER_ID_HEADER))
                .build();
    }
}
