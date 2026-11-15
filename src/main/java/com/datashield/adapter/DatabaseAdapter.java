package com.datashield.adapter;

import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Enterprise database adapter interface providing unified operations across any DBMS.
 */
public interface DatabaseAdapter {
    DatabaseType getType();

    /**
     * Validates credentials and server reachability before executing operations.
     */
    void testConnection(ConnectionConfig config) throws Exception;

    /**
     * Streams raw database backup data directly to the given OutputStream.
     */
    void streamBackup(ConnectionConfig config, BackupRequest request,
                      OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception;

    /**
     * Reads raw backup data from the given InputStream and restores it into the target database.
     */
    void streamRestore(ConnectionConfig config, RestoreRequest request,
                       InputStream sourceStream) throws Exception;

    /**
     * Captures current transactional log or checkpoint coordinates (e.g. Binlog position, WAL LSN).
     */
    Map<String, String> captureCoordinates(ConnectionConfig config) throws Exception;

    /**
     * Lists available tables or collections in the database.
     */
    List<String> listTablesOrCollections(ConnectionConfig config) throws Exception;

    /**
     * Whether this DBMS adapter natively supports incremental/differential backup extraction.
     */
    boolean supportsIncremental();
}

