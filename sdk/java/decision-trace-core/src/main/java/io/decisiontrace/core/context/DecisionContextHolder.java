package io.decisiontrace.core.context;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DecisionContextHolder {
    private static final ThreadLocal<Deque<DecisionFrame>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    public DecisionFrame current() {
        return STACK.get().peek();
    }

    public void push(DecisionFrame frame) {
        STACK.get().push(frame);
    }

    public void pop(DecisionFrame frame) {
        Deque<DecisionFrame> stack = STACK.get();
        DecisionFrame current = stack.peek();
        if (current == null) {
            return;
        }
        if (current != frame) {
            throw new IllegalStateException("Decision scope closed out of order");
        }
        stack.pop();
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public void clear() {
        STACK.remove();
    }
}
