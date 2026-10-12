package textmenu.runtime;

public final class ExecutionGuard {

    public static final long DEFAULT_MAX_STEPS = 25_000_000L;
    public static final long DEFAULT_MAX_SLICE_MILLIS = 10_000L;

    private static final long SLEEP_SLICE_MILLIS = 20L;
    private static final double MAX_SLEEP_SECONDS = 86_400.0;

    private final long maxSteps;
    private final long maxSliceNanos;
    private final long maxSliceMillis;
    private volatile boolean cancelled;
    private long steps;
    private long sliceStart;

    public ExecutionGuard() {
        this(DEFAULT_MAX_STEPS, DEFAULT_MAX_SLICE_MILLIS);
    }

    public ExecutionGuard(long maxSteps, long maxSliceMillis) {
        this.maxSteps = maxSteps;
        this.maxSliceMillis = maxSliceMillis;
        this.maxSliceNanos = maxSliceMillis * 1_000_000L;
        begin();
    }

    public void begin() {
        steps = 0;
        sliceStart = System.nanoTime();
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void step() {
        if (cancelled) throw cancelledAbort();
        if (++steps > maxSteps) {
            throw new ScriptAbort(ScriptAbort.Reason.LIMIT,
                    "Límite de instrucciones excedido (" + maxSteps + " sin pausar). "
                            + "Agrega time.sleep() dentro del bucle o usa el botón Detener.");
        }
        if ((steps & 0x3FF) == 0 && System.nanoTime() - sliceStart > maxSliceNanos) {
            throw new ScriptAbort(ScriptAbort.Reason.LIMIT,
                    "Límite de tiempo excedido (" + (maxSliceMillis / 1000) + " s sin pausar). "
                            + "Agrega time.sleep() dentro del bucle o usa el botón Detener.");
        }
    }

    public void sleep(double seconds) {
        double clamped = Math.min(Math.max(seconds, 0.001), MAX_SLEEP_SECONDS);
        long deadline = System.nanoTime() + (long) (clamped * 1_000_000_000L);
        while (true) {
            if (cancelled) throw cancelledAbort();
            long leftNanos = deadline - System.nanoTime();
            if (leftNanos <= 0) break;
            long millis = Math.max(1L, Math.min(SLEEP_SLICE_MILLIS, leftNanos / 1_000_000L));
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                if (cancelled) throw cancelledAbort();
            }
        }
        begin();
    }

    private static ScriptAbort cancelledAbort() {
        return new ScriptAbort(ScriptAbort.Reason.CANCELLED, "Ejecución detenida.");
    }
}
