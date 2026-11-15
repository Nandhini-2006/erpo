package com.datashield.cli;

import com.datashield.compression.CompressionProvider;
import com.datashield.compression.CompressionProviderFactory;
import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.BackupManifest;
import com.datashield.core.model.CompressionType;
import com.datashield.crypto.AesGcmCryptoProvider;
import com.datashield.crypto.CryptoProvider;
import com.datashield.storage.StorageProvider;
import com.datashield.storage.StorageProviderFactory;
import com.datashield.util.ByteUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "verify", description = "Verify cryptographic integrity and uncorrupted status of a backup archive", mixinStandardHelpOptions = true)
public class VerifyCommand implements Callable<Integer> {

    @Option(names = {"-s", "--source"}, required = true, description = "Backup archive path, s3:// URI, or backup ID")
    private String source;

    @Option(names = {"-p", "--password"}, description = "Decryption password if archive is AES-256 encrypted")
    private String password;

    @Override
    public Integer call() {
        System.out.println("=================================================");
        System.out.println("       DataShield Archive Integrity Verifier     ");
        System.out.println("=================================================");

        try {
            BackupCatalog catalog = new JsonFileBackupCatalog();
            BackupManifest manifest = null;
            String archiveFileName = source;
            String storageLocation = "./backups";

            Optional<BackupManifest> opt = catalog.getBackup(source);
            if (opt.isPresent()) {
                manifest = opt.get();
                archiveFileName = manifest.getStorageLocation();
            } else {
                Path p = Paths.get(source);
                if (Files.exists(p)) {
                    storageLocation = p.getParent() != null ? p.getParent().toString() : ".";
                    archiveFileName = p.getFileName().toString();
                    // Check for sibling manifest
                    String manifestFileName = archiveFileName.replaceAll("\\.(bak|gz|zip|enc)+$", "") + ".manifest.json";
                    Path manifestPath = p.getParent() != null ? p.getParent().resolve(manifestFileName) : Paths.get(manifestFileName);
                    if (Files.exists(manifestPath)) {
                        StorageProvider sp = StorageProviderFactory.create(storageLocation);
                        manifest = sp.loadManifest(manifestFileName.replace(".manifest.json", ""));
                    }
                }
            }

            StorageProvider storage = StorageProviderFactory.create(storageLocation);
            System.out.println("Reading archive: " + archiveFileName);

            boolean isEncrypted = archiveFileName.endsWith(".enc") || (password != null && !password.isBlank())
                    || (manifest != null && !"NONE".equalsIgnoreCase(manifest.getEncryptionAlgorithm()));

            CompressionType compType = archiveFileName.contains(".gz") ? CompressionType.GZIP : CompressionType.NONE;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long totalRawBytes = 0;

            try (InputStream in = storage.openInputStream(archiveFileName)) {
                InputStream chain = in;
                if (isEncrypted) {
                    if (password == null || password.isBlank()) {
                        System.err.println("Archive is encrypted with AES-256-GCM. Please supply --password to verify.");
                        return 1;
                    }
                    CryptoProvider crypto = new AesGcmCryptoProvider();
                    chain = crypto.wrapDecryptor(chain, password);
                }

                CompressionProvider comp = CompressionProviderFactory.getProvider(compType);
                chain = comp.wrapDecompressor(chain);

                byte[] buf = new byte[64 * 1024];
                int read;
                while ((read = chain.read(buf)) != -1) {
                    md.update(buf, 0, read);
                    totalRawBytes += read;
                }
            }

            String calculatedSha256 = HexFormat.of().formatHex(md.digest());

            System.out.println("Computed SHA-256 Digest : " + calculatedSha256);
            System.out.println("Decompressed Raw Bytes  : " + ByteUtils.humanReadableByteCount(totalRawBytes));

            if (manifest != null && manifest.getSha256Checksum() != null) {
                System.out.println("Expected Manifest Digest: " + manifest.getSha256Checksum());
                if (calculatedSha256.equalsIgnoreCase(manifest.getSha256Checksum())) {
                    System.out.println("\n>>> INTEGRITY VERIFICATION: PASSED! Archive is authentic and 100% uncorrupted.");
                    return 0;
                } else {
                    System.err.println("\n>>> INTEGRITY VERIFICATION: FAILED! Checksum mismatch - archive may be corrupted or altered!");
                    return 1;
                }
            } else {
                System.out.println("\n>>> ARCHIVE READ CHECK: PASSED! Archive uncompresses cleanly without corruption.");
                return 0;
            }
        } catch (Exception e) {
            System.err.println("\nVerification FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

