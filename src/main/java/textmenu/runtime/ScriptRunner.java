package textmenu.runtime;

import textmenu.draw.Scene;
import textmenu.interpreter.MCodeInterpreter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ScriptRunner {

    private static final long STACK_BYTES = 128L * 1024 * 1024;
    private static final long JOIN_MILLIS = 500L;
    private static final ConsoleBuffer CONSOLE = new ConsoleBuffer(2000);
    private static final Object LOCK = new Object();

    private static Thread thread;
    private static ExecutionGuard guard;
    private static volatile String legacyOwner = "legacy-idle";
    private static final AtomicLong RUN_IDS = new AtomicLong();
    private static final Map<String, ParallelRun> PARALLEL = new ConcurrentHashMap<>();
    private record ParallelRun(String owner, Thread thread, ExecutionGuard guard) {}

    private ScriptRunner() {
    }

    public static ConsoleBuffer console() {
        return CONSOLE;
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return thread != null && thread.isAlive();
        }
    }

    public static void start(String code) {
        synchronized (LOCK) {
            stopAndWait();
            Scene.global().clearOwner(legacyOwner);
            MainThreadQueue.clear();
            CONSOLE.clear();
            CONSOLE.append("[MCODE 2.4.1] Ejecutando script...\n");
            ExecutionGuard newGuard = new ExecutionGuard();
            guard = newGuard;
            legacyOwner = "legacy-" + RUN_IDS.incrementAndGet();
            String owner = legacyOwner;
            Thread created = new Thread(null, () -> body(code, newGuard, owner), "mcode-script", STACK_BYTES);
            thread = created;
            created.setDaemon(true);
            created.start();
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            signalStop();
        }
    }

    /** Starts a named script without stopping other named tabs. Each run has a unique scene owner. */
    public static void startParallel(String key, String code) {
        String id = (key == null || key.isBlank()) ? "untitled" : key;
        stopParallel(id);
        String owner = id + "#run-" + RUN_IDS.incrementAndGet();
        ExecutionGuard runGuard = new ExecutionGuard();
        Thread created = new Thread(null, () -> {
            CONSOLE.append("[MCODE] Ejecutando " + id + "\n");
            try {
                MCodeInterpreter.run(code, runGuard, CONSOLE::append, owner);
                CONSOLE.append("[MCODE] Finalizó " + id + "\n");
            } catch (Throwable t) {
                CONSOLE.append("[MCODE] Error en " + id + ": " + t.getMessage() + "\n");
            } finally {
                Scene.global().clearOwner(owner);
                PARALLEL.compute(id, (k, current) -> current != null && current.owner().equals(owner) ? null : current);
            }
        }, "mcode-" + id.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + RUN_IDS.get(), STACK_BYTES);
        created.setDaemon(true);
        PARALLEL.put(id, new ParallelRun(owner, created, runGuard));
        created.start();
    }

    public static void stopParallel(String key) {
        ParallelRun run = PARALLEL.remove(key);
        if (run != null) {
            run.guard().cancel();
            run.thread().interrupt();
            Scene.global().clearOwner(run.owner());
        }
    }

    public static boolean isParallelRunning(String key) {
        ParallelRun run = PARALLEL.get(key);
        return run != null && run.thread().isAlive();
    }

    public static void stopAllParallel() {
        for (String key : PARALLEL.keySet()) stopParallel(key);
    }

    public static void shutdown() {
        synchronized (LOCK) { stopAndWait(); }
        stopAllParallel();
    }

    private static void signalStop() {
        if (guard != null) guard.cancel();
        if (thread != null && thread.isAlive()) thread.interrupt();
        Scene.global().clearOwner(legacyOwner);
    }

    private static void stopAndWait() {
        signalStop();
        Thread previous = thread;
        if (previous == null || previous == Thread.currentThread()) return;
        try {
            previous.join(JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void body(String code, ExecutionGuard runGuard, String owner) {
        boolean[] printed = {false};
        try {
            MCodeInterpreter.run(code, runGuard, text -> {
                if (!text.isEmpty()) printed[0] = true;
                CONSOLE.append(text);
            }, owner);
            if (!printed[0]) CONSOLE.append("Código ejecutado correctamente (sin salida)\n");
        } catch (ScriptAbort abort) {
            CONSOLE.append(abort.reason() == ScriptAbort.Reason.CANCELLED
                    ? abort.getMessage() + "\n"
                    : "Error: " + abort.getMessage() + "\n");
        } catch (StackOverflowError e) {
            CONSOLE.append("Error: recursión demasiado profunda\n");
        } catch (OutOfMemoryError e) {
            CONSOLE.append("Error: memoria insuficiente\n");
        } catch (Throwable t) {
            CONSOLE.append("Error interno: " + t + "\n");
        } finally {
            Scene.global().clearOwner(owner);
        }
    }
}
