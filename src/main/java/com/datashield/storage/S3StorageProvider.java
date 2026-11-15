package com.datashield.storage;

import com.datashield.core.model.BackupManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Enterprise AWS S3 and S3-compatible (MinIO, Cloudflare R2, Ceph) cloud storage provider.
 */
public class S3StorageProvider implements StorageProvider {
    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final String bucketName;
    private final String prefix;
    private final S3Client s3Client;

    public S3StorageProvider(String s3Uri) {
        // Parse s3://bucket/optional/prefix
        URI uri = URI.create(s3Uri);
        this.bucketName = uri.getHost();
        String path = uri.getPath();
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path != null && !path.isBlank() && !path.endsWith("/")) {
            path += "/";
        }
        this.prefix = path != null ? path : "";

        this.s3Client = createS3Client();
    }

    public S3StorageProvider(String bucketName, String prefix, S3Client s3Client) {
        this.bucketName = bucketName;
        this.prefix = prefix != null && !prefix.endsWith("/") && !prefix.isBlank() ? prefix + "/" : (prefix != null ? prefix : "");
        this.s3Client = s3Client;
    }

    private S3Client createS3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create());

        String regionEnv = System.getenv("AWS_REGION");
        if (regionEnv != null && !regionEnv.isBlank()) {
            builder.region(Region.of(regionEnv));
        } else {
            builder.region(Region.US_EAST_1);
        }

        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true); // Needed for MinIO / localstack
        }

        return builder.build();
    }

    @Override
    public OutputStream openOutputStream(String archiveName) throws IOException {
        String key = prefix + archiveName;
        Path tempFile = Files.createTempFile("datashield_s3_upload_", ".tmp");
        log.info("Streaming to local spool before S3 upload: s3://{}/{}", bucketName, key);

        return new BufferedOutputStream(Files.newOutputStream(tempFile)) {
            @Override
            public void close() throws IOException {
                super.close();
                try {
                    log.info("Uploading spooled archive to AWS S3: s3://{}/{} ({} bytes)",
                            bucketName, key, Files.size(tempFile));
                    PutObjectRequest putReq = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build();
                    s3Client.putObject(putReq, RequestBody.fromFile(tempFile));
                    log.info("S3 upload completed successfully for: s3://{}/{}", bucketName, key);
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            }
        };
    }

    @Override
    public InputStream openInputStream(String archiveName) throws IOException {
        String key = prefix + archiveName;
        log.info("Streaming backup archive from AWS S3: s3://{}/{}", bucketName, key);
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getReq);
        return new BufferedInputStream(s3Stream);
    }

    @Override
    public void saveManifest(BackupManifest manifest) throws IOException {
        String key = prefix + manifest.getBackupId() + ".manifest.json";
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(manifest);
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build();
        s3Client.putObject(putReq, RequestBody.fromBytes(bytes));
        log.info("Saved backup manifest to S3: s3://{}/{}", bucketName, key);
    }

    @Override
    public BackupManifest loadManifest(String backupId) throws IOException {
        String key = prefix + backupId + ".manifest.json";
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try (InputStream in = s3Client.getObject(getReq)) {
            return OBJECT_MAPPER.readValue(in, BackupManifest.class);
        }
    }

    @Override
    public List<BackupManifest> listBackups() throws IOException {
        List<BackupManifest> manifests = new ArrayList<>();
        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response res = s3Client.listObjectsV2(listReq);
        for (S3Object s3Object : res.contents()) {
            if (s3Object.key().endsWith(".manifest.json")) {
                try (InputStream in = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build())) {
                    manifests.add(OBJECT_MAPPER.readValue(in, BackupManifest.class));
                } catch (Exception e) {
                    log.warn("Failed to load S3 manifest {}: {}", s3Object.key(), e.getMessage());
                }
            }
        }

        manifests.sort(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return manifests;
    }

    @Override
    public boolean deleteBackup(String backupId) throws IOException {
        String manifestKey = prefix + backupId + ".manifest.json";
        try {
            BackupManifest m = loadManifest(backupId);
            if (m.getStorageLocation() != null) {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(prefix + m.getStorageLocation()).build());
            }
        } catch (Exception ignored) {
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(manifestKey).build());
        return true;
    }

    @Override
    public void testConnectivity() throws IOException {
        try {
            HeadBucketRequest headBucket = HeadBucketRequest.builder().bucket(bucketName).build();
            s3Client.headBucket(headBucket);
        } catch (S3Exception e) {
            throw new IOException("AWS S3 bucket connectivity failed for '" + bucketName + "': " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public String getLocationDescription() {
        return "AWS S3: s3://" + bucketName + "/" + prefix;
    }
}

