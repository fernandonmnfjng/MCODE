package textmenu.interpreter;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface NativeFunction {
    Object call(List<Object> args, Map<String, Object> kwargs);
}
