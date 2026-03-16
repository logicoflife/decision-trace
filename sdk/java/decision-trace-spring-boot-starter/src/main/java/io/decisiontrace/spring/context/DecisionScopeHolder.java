package io.decisiontrace.spring.context;

import io.decisiontrace.core.DecisionScope;
import java.util.ArrayDeque;
import java.util.Deque;

public final class DecisionScopeHolder {
    private static final ThreadLocal<Deque<DecisionScope>> CURRENT =
            ThreadLocal.withInitial(ArrayDeque::new);

    public void push(DecisionScope scope) {
        CURRENT.get().push(scope);
    }

    public DecisionScope current() {
        return CURRENT.get().peek();
    }

    public void pop(DecisionScope scope) {
        Deque<DecisionScope> scopes = CURRENT.get();
        if (scopes.isEmpty()) {
            return;
        }
        if (scopes.peek() == scope) {
            scopes.pop();
        } else {
            scopes.remove(scope);
        }
        if (scopes.isEmpty()) {
            CURRENT.remove();
        }
    }

    public void clear() {
        CURRENT.remove();
    }
}
