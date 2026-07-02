package com.im.njams.sdk.communication.it;

import java.util.function.BooleanSupplier;

/** Polls a condition until it becomes true or a deadline passes. Avoids fixed-duration sleeps in tests. */
final class Await {
    private Await() {
    }

    static boolean until(BooleanSupplier condition, long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return condition.getAsBoolean();
            }
        }
        return condition.getAsBoolean();
    }
}
