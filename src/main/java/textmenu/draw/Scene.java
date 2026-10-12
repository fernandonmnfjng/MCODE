package textmenu.draw;

import textmenu.runtime.ScriptError;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Scene {

    public static final int MAX_SHAPES = 5_000;
    public static final int MAX_VERTICES = 200_000;

    private static final Scene GLOBAL = new Scene();

    private volatile ConcurrentSkipListMap<Long, Shape> shapes = new ConcurrentSkipListMap<>();
    private final Map<String, ConcurrentSkipListMap<Long, Shape>> stagedFrames = new ConcurrentHashMap<>();
    private final Map<String, Integer> stagedVertices = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private int vertices;

    public static Scene global() { return GLOBAL; }
    public long nextId() { return ids.incrementAndGet(); }

    public synchronized void add(Shape shape) {
        int cost = shape.vertexCount();
        int pendingShapeCount = stagedFrames.values().stream().mapToInt(Map::size).sum();
        int pendingVertexCount = stagedVertices.values().stream().mapToInt(Integer::intValue).sum();
        if (shapes.size() + pendingShapeCount >= MAX_SHAPES) {
            throw ScriptError.runtime("Límite de escena: máximo " + MAX_SHAPES + " formas. Usa mc.remove() o mc.clear() para liberar espacio.");
        }
        if (vertices + pendingVertexCount + cost > MAX_VERTICES) {
            throw ScriptError.runtime("Límite de escena: máximo " + MAX_VERTICES + " vértices en total. Reduce la geometría o usa mc.clear().");
        }
        ConcurrentSkipListMap<Long, Shape> frame = stagedFrames.get(shape.owner());
        if (frame != null) {
            frame.put(shape.id(), shape);
            stagedVertices.merge(shape.owner(), cost, Integer::sum);
        } else {
            shapes.put(shape.id(), shape);
            vertices += cost;
        }
    }

    public synchronized boolean remove(long id) {
        Shape removed = shapes.remove(id);
        if (removed != null) {
            vertices -= removed.vertexCount();
            removed.release();
            return true;
        }
        for (Map.Entry<String, ConcurrentSkipListMap<Long, Shape>> entry : stagedFrames.entrySet()) {
            removed = entry.getValue().remove(id);
            if (removed != null) {
                stagedVertices.merge(entry.getKey(), -removed.vertexCount(), Integer::sum);
                removed.release();
                return true;
            }
        }
        return false;
    }

    public synchronized int clear() {
        ConcurrentSkipListMap<Long, Shape> old = shapes;
        shapes = new ConcurrentSkipListMap<>();
        int count = old.size();
        for (Shape shape : old.values()) shape.release();
        for (ConcurrentSkipListMap<Long, Shape> frame : stagedFrames.values()) {
            count += frame.size();
            for (Shape shape : frame.values()) shape.release();
        }
        stagedFrames.clear();
        stagedVertices.clear();
        vertices = 0;
        return count;
    }

    public synchronized int clearGroup(String group) {
        int count = 0;
        ConcurrentSkipListMap<Long, Shape> replacement = new ConcurrentSkipListMap<>(shapes);
        Iterator<Shape> it = replacement.values().iterator();
        while (it.hasNext()) {
            Shape shape = it.next();
            if (!shape.group().equals(group)) continue;
            it.remove(); vertices -= shape.vertexCount(); shape.release(); count++;
        }
        shapes = replacement;
        for (Map.Entry<String, ConcurrentSkipListMap<Long, Shape>> entry : stagedFrames.entrySet()) {
            Iterator<Shape> pending = entry.getValue().values().iterator();
            while (pending.hasNext()) {
                Shape shape = pending.next();
                if (!shape.group().equals(group)) continue;
                pending.remove(); stagedVertices.merge(entry.getKey(), -shape.vertexCount(), Integer::sum); shape.release(); count++;
            }
        }
        return count;
    }

    /** Starts a staging frame. Its shapes stay hidden until endFrame swaps them into the scene. */
    public synchronized void beginFrame(String owner) {
        String key = owner == null ? "" : owner;
        if (stagedFrames.containsKey(key)) throw ScriptError.runtime("mc.begin_frame(): ya hay un frame abierto; llama a mc.end_frame() primero.");
        stagedFrames.put(key, new ConcurrentSkipListMap<>());
        stagedVertices.put(key, 0);
    }

    /** Atomically replaces all visible shapes belonging to this script with its newly drawn frame. */
    public synchronized int endFrame(String owner) {
        String key = owner == null ? "" : owner;
        ConcurrentSkipListMap<Long, Shape> pending = stagedFrames.remove(key);
        if (pending == null) throw ScriptError.runtime("mc.end_frame(): no hay un frame abierto; llama a mc.begin_frame() primero.");
        stagedVertices.remove(key);
        ConcurrentSkipListMap<Long, Shape> old = shapes;
        ConcurrentSkipListMap<Long, Shape> replacement = new ConcurrentSkipListMap<>(old);
        int nextVertices = vertices;
        Iterator<Shape> it = replacement.values().iterator();
        while (it.hasNext()) {
            Shape shape = it.next();
            if (!shape.owner().equals(key)) continue;
            it.remove(); nextVertices -= shape.vertexCount();
        }
        for (Shape shape : pending.values()) { replacement.put(shape.id(), shape); nextVertices += shape.vertexCount(); }
        if (replacement.size() > MAX_SHAPES || nextVertices > MAX_VERTICES) {
            for (Shape shape : pending.values()) shape.release();
            throw ScriptError.runtime("mc.end_frame(): el nuevo frame excede los límites de escena.");
        }
        shapes = replacement;
        vertices = nextVertices;
        for (Shape shape : old.values()) if (shape.owner().equals(key)) shape.release();
        return pending.size();
    }

    public synchronized boolean isFrameOpen(String owner) { return stagedFrames.containsKey(owner == null ? "" : owner); }

    public synchronized int clearStagedOwner(String owner) {
        String key = owner == null ? "" : owner;
        ConcurrentSkipListMap<Long, Shape> pending = stagedFrames.get(key);
        if (pending == null) return 0;
        int count = pending.size();
        for (Shape shape : pending.values()) shape.release();
        pending.clear(); stagedVertices.put(key, 0);
        return count;
    }

    /** Removes every visible and staged shape owned by one script execution. */
    public synchronized int clearOwner(String owner) {
        String key = owner == null ? "" : owner;
        ConcurrentSkipListMap<Long, Shape> old = shapes;
        ConcurrentSkipListMap<Long, Shape> replacement = new ConcurrentSkipListMap<>(old);
        int count = 0;
        Iterator<Shape> it = replacement.values().iterator();
        while (it.hasNext()) {
            Shape shape = it.next();
            if (!shape.owner().equals(key)) continue;
            it.remove(); vertices -= shape.vertexCount(); count++;
        }
        shapes = replacement;
        for (Shape shape : old.values()) if (shape.owner().equals(key)) shape.release();
        ConcurrentSkipListMap<Long, Shape> pending = stagedFrames.remove(key);
        stagedVertices.remove(key);
        if (pending != null) {
            count += pending.size();
            for (Shape shape : pending.values()) shape.release();
        }
        return count;
    }

    /** Removes one owner-scoped named group without clearing other scripts. */
    public synchronized int clearOwnerGroup(String owner, String group) {
        String key = owner == null ? "" : owner;
        String groupKey = group == null ? "" : group;
        ConcurrentSkipListMap<Long, Shape> old = shapes;
        ConcurrentSkipListMap<Long, Shape> replacement = new ConcurrentSkipListMap<>(old);
        int count = 0;
        Iterator<Shape> it = replacement.values().iterator();
        while (it.hasNext()) {
            Shape shape = it.next();
            if (!shape.owner().equals(key) || !shape.group().equals(groupKey)) continue;
            it.remove(); vertices -= shape.vertexCount(); shape.release(); count++;
        }
        shapes = replacement;
        ConcurrentSkipListMap<Long, Shape> pending = stagedFrames.get(key);
        if (pending != null) {
            Iterator<Shape> pendingIt = pending.values().iterator();
            while (pendingIt.hasNext()) {
                Shape shape = pendingIt.next();
                if (!shape.group().equals(groupKey)) continue;
                pendingIt.remove(); stagedVertices.merge(key, -shape.vertexCount(), Integer::sum); shape.release(); count++;
            }
        }
        return count;
    }

    public Collection<Shape> shapes() { return shapes.values(); }
    public int size() { return shapes.size(); }
}
