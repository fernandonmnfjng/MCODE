package textmenu.runtime;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainThreadQueue {

    private static final int CAPACITY = 512;
    private static final int MAX_PER_TICK = 128;

    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger();

    private MainThreadQueue() {
    }

    public static boolean offer(Runnable task) {
        if (SIZE.incrementAndGet() > CAPACITY) {
            SIZE.decrementAndGet();
            return false;
        }
        TASKS.add(task);
        return true;
    }

    public static void post(Runnable task, ExecutionGuard guard) {
        while (!offer(task)) guard.sleep(0.005);
    }

    public static void drain() {
        for (int i = 0; i < MAX_PER_TICK; i++) {
            Runnable task = TASKS.poll();
            if (task == null) return;
            SIZE.decrementAndGet();
            try {
                task.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void clear() {
        while (TASKS.poll() != null) SIZE.decrementAndGet();
    }
}
