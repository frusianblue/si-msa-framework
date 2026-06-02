package com.company.framework.saga;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** saga 타입명 → 정의 매핑. 앱이 등록한 {@link SagaDefinition} 빈들을 모아 보관한다. */
public class SagaRegistry {

    private final ConcurrentMap<String, SagaDefinition> definitions = new ConcurrentHashMap<>();

    public SagaRegistry() {}

    public SagaRegistry(Collection<SagaDefinition> initial) {
        if (initial != null) {
            initial.forEach(this::register);
        }
    }

    public void register(SagaDefinition definition) {
        definitions.put(definition.name(), definition);
    }

    public SagaDefinition get(String name) {
        SagaDefinition def = definitions.get(name);
        if (def == null) {
            throw new IllegalArgumentException("등록되지 않은 saga 타입: " + name);
        }
        return def;
    }

    public boolean contains(String name) {
        return definitions.containsKey(name);
    }
}
