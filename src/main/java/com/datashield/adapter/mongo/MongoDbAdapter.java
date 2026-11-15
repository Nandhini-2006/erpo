package com.datashield.adapter.mongo;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;
import com.datashield.core.model.TableFilter;
import com.datashield.util.ProcessRunner;
import com.mongodb.client.*;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MongoDbAdapter implements DatabaseAdapter {
    private static final Logger log = LoggerFactory.getLogger(MongoDbAdapter.class);

    @Override
    public DatabaseType getType() {
        return DatabaseType.MONGODB;
    }

    @Override
    public void testConnection(ConnectionConfig config) throws Exception {
        log.info("Testing MongoDB connection to {}:{}", config.getHost(), config.getPort());
        try (MongoClient client = MongoClients.create(config.getJdbcUrl())) {
            MongoDatabase admin = client.getDatabase("admin");
            Document ping = admin.runCommand(new Document("ping", 1));
            log.info("Successfully connected to MongoDB: {}", ping.toJson());
        }
    }

    @Override
    public void streamBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception {
        try {
            coordinatesOut.putAll(captureCoordinates(config));
        } catch (Exception e) {
            log.warn("Could not capture MongoDB coordinates: {}", e.getMessage());
        }

        if (ProcessRunner.isCommandAvailable("mongodump")) {
            log.info("Executing native mongodump streaming archive...");
            List<String> cmd = new ArrayList<>();
            cmd.add("mongodump");
            cmd.add("--uri=" + config.getJdbcUrl());
            cmd.add("--archive");

            if (!request.getIncludeTables().isEmpty()) {
                // MongoDB dumps first collection specified
                String coll = request.getIncludeTables().iterator().next();
                cmd.add("--collection=" + coll);
            }

            Process process = ProcessRunner.startProcess(cmd, Collections.emptyMap());
            String stderr = ProcessRunner.captureStderr(process);

            try (InputStream in = new BufferedInputStream(process.getInputStream())) {
                in.transferTo(targetStream);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("mongodump failed with exit code " + exitCode + ": " + stderr);
            }
        } else {
            log.info("Native mongodump not detected on PATH. Using MongoDB Java Driver document streaming engine.");
            streamMongoJavaDriverBackup(config, request, targetStream);
        }
    }

    private void streamMongoJavaDriverBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream) throws Exception {
        TableFilter filter = request.toTableFilter();
        try (MongoClient client = MongoClients.create(config.getJdbcUrl())) {
            MongoDatabase db = client.getDatabase(config.getDatabase() != null ? config.getDatabase() : "test");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(targetStream, StandardCharsets.UTF_8));

            for (String collName : db.listCollectionNames()) {
                if (!filter.shouldInclude(collName)) {
                    continue;
                }
                log.info("Dumping MongoDB collection: {}", collName);
                MongoCollection<Document> collection = db.getCollection(collName);
                writer.write("--- COLLECTION: " + collName + " ---\n");

                for (Document doc : collection.find()) {
                    writer.write(doc.toJson());
                    writer.write("\n");
                }
                writer.flush();
            }
            writer.write("--- END DATA ---\n");
            writer.flush();
        }
    }

    @Override
    public void streamRestore(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        if (ProcessRunner.isCommandAvailable("mongorestore")) {
            log.info("Executing native mongorestore streaming archive...");
            List<String> cmd = new ArrayList<>();
            cmd.add("mongorestore");
            cmd.add("--uri=" + config.getJdbcUrl());
            cmd.add("--archive");
            if (request.isDropExistingTables()) {
                cmd.add("--drop");
            }

            Process process = ProcessRunner.startProcess(cmd, Collections.emptyMap());
            String stderr = ProcessRunner.captureStderr(process);

            try (OutputStream out = new BufferedOutputStream(process.getOutputStream())) {
                sourceStream.transferTo(out);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("mongorestore failed with exit code " + exitCode + ": " + stderr);
            }
        } else {
            log.info("Native mongorestore not detected on PATH. Restoring via MongoDB Java Driver.");
            restoreMongoJavaDriver(config, request, sourceStream);
        }
    }

    private void restoreMongoJavaDriver(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        TableFilter filter = request.toTableFilter();
        try (MongoClient client = MongoClients.create(config.getJdbcUrl())) {
            MongoDatabase db = client.getDatabase(config.getDatabase() != null ? config.getDatabase() : "test");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sourceStream, StandardCharsets.UTF_8))) {
                String line;
                String currentCollectionName = null;
                List<Document> batch = new ArrayList<>();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("--- COLLECTION: ")) {
                        // Flush previous batch
                        if (currentCollectionName != null && !batch.isEmpty()) {
                            db.getCollection(currentCollectionName).insertMany(batch);
                            batch.clear();
                        }
                        currentCollectionName = line.substring(16, line.indexOf(" ---"));
                        if (request.isDropExistingTables() && filter.shouldInclude(currentCollectionName)) {
                            db.getCollection(currentCollectionName).drop();
                        }
                        continue;
                    }
                    if (line.equals("--- END DATA ---")) {
                        break;
                    }
                    if (currentCollectionName != null && filter.shouldInclude(currentCollectionName) && !line.isBlank()) {
                        batch.add(Document.parse(line));
                        if (batch.size() >= 500) {
                            db.getCollection(currentCollectionName).insertMany(batch);
                            batch.clear();
                        }
                    }
                }
                if (currentCollectionName != null && !batch.isEmpty()) {
                    db.getCollection(currentCollectionName).insertMany(batch);
                    batch.clear();
                }
            }
        }
    }

    @Override
    public Map<String, String> captureCoordinates(ConnectionConfig config) throws Exception {
        Map<String, String> coords = new HashMap<>();
        try (MongoClient client = MongoClients.create(config.getJdbcUrl())) {
            Document status = client.getDatabase("admin").runCommand(new Document("serverStatus", 1));
            Document optime = status.get("oplog", Document.class);
            if (optime != null) {
                coords.put("mongo_oplog", optime.toJson());
            }
            Object clusterTime = status.get("$clusterTime");
            if (clusterTime instanceof Document d) {
                BsonTimestamp ts = d.get("clusterTime", BsonTimestamp.class);
                if (ts != null) {
                    coords.put("cluster_time", ts.getTime() + ":" + ts.getInc());
                }
            }
        }
        return coords;
    }

    @Override
    public List<String> listTablesOrCollections(ConnectionConfig config) throws Exception {
        List<String> collections = new ArrayList<>();
        try (MongoClient client = MongoClients.create(config.getJdbcUrl())) {
            MongoDatabase db = client.getDatabase(config.getDatabase() != null ? config.getDatabase() : "test");
            for (String name : db.listCollectionNames()) {
                collections.add(name);
            }
        }
        return collections;
    }

    @Override
    public boolean supportsIncremental() {
        return true;
    }
}

