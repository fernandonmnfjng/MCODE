package textmenu.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeObject {

    private final String name;
    private final Map<String, Object> members = new LinkedHashMap<>();

    public NativeObject(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Map<String, Object> members() {
        return members;
    }

    public NativeObject put(String member, Object value) {
        members.put(member, value);
        return this;
    }
}
