package com.datashield;

import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.BackupManifest;
import com.datashield.core.model.BackupType;
import com.datashield.core.model.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackupCatalogTest {

    @Test
    void shouldRegisterAndResolveLineage(@TempDir Path tempDir) throws Exception {
        Path catalogFile = tempDir.resolve("catalog.json");
        JsonFileBackupCatalog catalog = new JsonFileBackupCatalog(catalogFile);

        String fullId = UUID.randomUUID().toString();
        BackupManifest fullManifest = new BackupManifest();
        fullManifest.setBackupId(fullId);
        fullManifest.setDatabaseName("prod_db");
        fullManifest.setDatabaseType(DatabaseType.POSTGRESQL);
        fullManifest.setBackupType(BackupType.FULL);
        fullManifest.setStartTime(Instant.now().minusSeconds(3600));
        fullManifest.setStatus("SUCCESS");
        catalog.register(fullManifest);

        String diffId = UUID.randomUUID().toString();
        BackupManifest diffManifest = new BackupManifest();
        diffManifest.setBackupId(diffId);
        diffManifest.setParentBackupId(fullId);
        diffManifest.setDatabaseName("prod_db");
        diffManifest.setDatabaseType(DatabaseType.POSTGRESQL);
        diffManifest.setBackupType(BackupType.DIFFERENTIAL);
        diffManifest.setStartTime(Instant.now().minusSeconds(1800));
        diffManifest.setStatus("SUCCESS");
        catalog.register(diffManifest);

        String incrId = UUID.randomUUID().toString();
        BackupManifest incrManifest = new BackupManifest();
        incrManifest.setBackupId(incrId);
        incrManifest.setParentBackupId(diffId);
        incrManifest.setDatabaseName("prod_db");
        incrManifest.setDatabaseType(DatabaseType.POSTGRESQL);
        incrManifest.setBackupType(BackupType.INCREMENTAL);
        incrManifest.setStartTime(Instant.now());
        incrManifest.setStatus("SUCCESS");
        catalog.register(incrManifest);

        // Verify retrieval
        assertThat(catalog.getBackup(fullId)).isPresent();
        assertThat(catalog.getBackup(incrId)).isPresent();

        // Verify latest full
        assertThat(catalog.getLatestBackup("prod_db", BackupType.FULL).get().getBackupId()).isEqualTo(fullId);

        // Verify lineage resolution chain (Full -> Diff -> Incr)
        List<BackupManifest> lineage = catalog.getLineageChain(incrId);
        assertThat(lineage).hasSize(3);
        assertThat(lineage.get(0).getBackupId()).isEqualTo(fullId);
        assertThat(lineage.get(1).getBackupId()).isEqualTo(diffId);
        assertThat(lineage.get(2).getBackupId()).isEqualTo(incrId);
    }
}

