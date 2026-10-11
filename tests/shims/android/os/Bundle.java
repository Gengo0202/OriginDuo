package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Desktop stand-in for {@code android.os.Bundle}.
 *
 * <p>Only the calls {@link io.github.yixing233.hyperduo.TrioSettings} makes on the way in
 * and out are replayed here: the broadcast-extras path is not exercised by the
 * offline preview, it just has to compile so the real {@code TrioSettings} can
 * be linked against the desktop shims.
 */
public class Bundle {

    private final Map<String, Object> values = new HashMap<>();

    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    public void putInt(String key, int value) {
        values.put(key, value);
    }

    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    public Set<String> keySet() {
        return values.keySet();
    }
}
