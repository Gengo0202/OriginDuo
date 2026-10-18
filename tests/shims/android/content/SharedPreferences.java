package android.content;

/** Minimal desktop stand-in for {@code android.content.SharedPreferences}. */
public interface SharedPreferences {

    boolean getBoolean(String key, boolean defValue);

    int getInt(String key, int defValue);

    /** The ring layout is a string; the real interface has this overload. */
    String getString(String key, String defValue);

    boolean contains(String key);

    interface Editor {
        Editor putBoolean(String key, boolean value);

        Editor putInt(String key, int value);

        Editor putString(String key, String value);

        boolean commit();
    }

    Editor edit();
}
