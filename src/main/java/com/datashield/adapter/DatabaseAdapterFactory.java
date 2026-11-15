package com.datashield.adapter;

import com.datashield.adapter.generic.GenericJdbcAdapter;
import com.datashield.adapter.mongo.MongoDbAdapter;
import com.datashield.adapter.mysql.MySqlAdapter;
import com.datashield.adapter.postgres.PostgreSqlAdapter;
import com.datashield.adapter.sqlite.SqliteAdapter;
import com.datashield.core.model.DatabaseType;

import java.util.EnumMap;
import java.util.Map;

public final class DatabaseAdapterFactory {
    private static final Map<DatabaseType, DatabaseAdapter> ADAPTERS = new EnumMap<>(DatabaseType.class);

    static {
        ADAPTERS.put(DatabaseType.MYSQL, new MySqlAdapter());
        ADAPTERS.put(DatabaseType.POSTGRESQL, new PostgreSqlAdapter());
        ADAPTERS.put(DatabaseType.MONGODB, new MongoDbAdapter());
        ADAPTERS.put(DatabaseType.SQLITE, new SqliteAdapter());
        ADAPTERS.put(DatabaseType.GENERIC_JDBC, new GenericJdbcAdapter());
    }

    private DatabaseAdapterFactory() {
    }

    public static DatabaseAdapter getAdapter(DatabaseType type) {
        if (type == null) {
            throw new IllegalArgumentException("DatabaseType cannot be null");
        }
        DatabaseAdapter adapter = ADAPTERS.get(type);
        if (adapter == null) {
            throw new UnsupportedOperationException("No database adapter registered for: " + type);
        }
        return adapter;
    }
}

