package droidkit.sqlite;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import droidkit.util.Dynamic;
import droidkit.util.DynamicException;

/**
 * @author Daniel Serdyukov
 */
public abstract class SQLite {

    private static final String SUFFIX = "$SQLiteTable";

    private static final AtomicReference<String> AUTHORITY_REF = new AtomicReference<>();

    private static final Map<Class<?>, SQLiteTable<?>> TABLES = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Uri> URIS = new ConcurrentHashMap<>();

    public static SQLite with(@NonNull Context context) {
        return new SQLiteImpl(context.getContentResolver(), AUTHORITY_REF.get());
    }

    public static SQLite with(@NonNull Context context, @NonNull String authority) {
        AUTHORITY_REF.lazySet(authority);
        return new SQLiteImpl(context.getContentResolver(), authority);
    }

    @SuppressWarnings("unused")
    static void attach(@NonNull String authority) {
        AUTHORITY_REF.lazySet(authority);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    static <T> SQLiteTable<T> getTable(@NonNull Class<?> type) {
        try {
            SQLiteTable<?> proxy = TABLES.get(type);
            if (proxy == null) {
                proxy = Dynamic.init(type.getName() + SUFFIX);
                TABLES.put(type, proxy);
            }
            return (SQLiteTable<T>) proxy;
        } catch (DynamicException e) {
            throw new SQLiteException(e);
        }
    }

    @NonNull
    @SuppressWarnings("unchecked")
    static Uri getUri(@NonNull Class<?> type) {
        Uri uri = URIS.get(type);
        if (uri == null) {
            uri = new Uri.Builder()
                    .scheme(SQLiteProvider.SCHEME)
                    .authority(AUTHORITY_REF.get())
                    .appendPath(getTable(type).getTableName())
                    .build();
            URIS.put(type, uri);
        }
        return uri;
    }

    public abstract void beginTransaction();

    public abstract void commitTransaction();

    @NonNull
    public abstract SQLite insert(@NonNull Object object);

    @NonNull
    public abstract SQLite update(@NonNull Object object);

    @NonNull
    public abstract SQLite delete(@NonNull Object object);

    @NonNull
    public abstract <T> SQLiteQuery<T> where(@NonNull Class<T> type);

    @NonNull
    public abstract <T> SQLiteResult<T> all(@NonNull Class<T> type);

}
