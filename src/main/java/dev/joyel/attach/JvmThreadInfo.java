package dev.joyel.attach;

import java.util.List;

public record JvmThreadInfo(
        long id,
        String name,
        String state,
        boolean daemon,
        int priority,
        String lockName,
        String lockOwnerName,
        List<String> stack) {
}
