package com.company.framework.saga;

import java.util.ArrayList;
import java.util.List;

/**
 * saga 타입 하나의 단계 시퀀스. 앱이 빌더로 선언해 {@link SagaRegistry} 에 등록한다.
 *
 * <pre>{@code
 * SagaDefinition order = SagaDefinition.named("OrderSaga")
 *     .step("reserveStock",  "stock-cmd",    "ReserveStock",   "stock-cmd",   "ReleaseStock")
 *     .step("processPayment","payment-cmd",  "ProcessPayment", "payment-cmd", "RefundPayment")
 *     .step("arrangeShipping","shipping-cmd","ArrangeShipping") // 보상 없음
 *     .build();
 * }</pre>
 */
public final class SagaDefinition {

    private final String name;
    private final List<SagaStep> steps;

    private SagaDefinition(String name, List<SagaStep> steps) {
        this.name = name;
        this.steps = steps;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public List<SagaStep> steps() {
        return steps;
    }

    public int size() {
        return steps.size();
    }

    public SagaStep step(int index) {
        return steps.get(index);
    }

    public static final class Builder {
        private final String name;
        private final List<SagaStep> steps = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }

        /** 보상 없는 단계. */
        public Builder step(String stepName, String commandTopic, String commandType) {
            steps.add(new SagaStep(stepName, commandTopic, commandType, null, null));
            return this;
        }

        /** 보상 있는 단계. */
        public Builder step(
                String stepName,
                String commandTopic,
                String commandType,
                String compensationTopic,
                String compensationType) {
            steps.add(new SagaStep(stepName, commandTopic, commandType, compensationTopic, compensationType));
            return this;
        }

        public SagaDefinition build() {
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("saga '" + name + "' 에 단계가 없습니다.");
            }
            return new SagaDefinition(name, List.copyOf(steps));
        }
    }
}
