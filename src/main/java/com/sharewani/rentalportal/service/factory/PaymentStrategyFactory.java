package com.sharewani.rentalportal.service.factory;

import com.sharewani.rentalportal.service.strategy.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;

// @Component
// public class PaymentStrategyFactory {

//     private final Map<String, PaymentStrategy> strategyMap;

//     public PaymentStrategyFactory(List<PaymentStrategy> strategies) {
//         this.strategyMap = strategies.stream()
//                 .collect(Collectors.toMap(PaymentStrategy::getType, Function.identity()));
//     }

//     public PaymentStrategy getStrategy(String type) {
//         PaymentStrategy strategy = strategyMap.get(type);
//         if (strategy == null) {
//             throw new IllegalArgumentException("No strategy found for type: " + type);
//         }
//         return strategy;
//     }

//     public List<String> getAvailableTypes() {
//         return strategyMap.keySet().stream().toList();
//     }
// }


@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final ApplicationContext context;

    public PaymentStrategy getStrategy(String method) {
        try {
            return (PaymentStrategy) context.getBean(method.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
    }
}
