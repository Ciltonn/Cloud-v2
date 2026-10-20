package org.project.cloud.file.service;

import io.minio.*;

import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.exception.ConflictException;
import org.project.cloud.exception.NotFoundException;
import org.project.cloud.file.model.Type;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.project.cloud.file.util.StoragePaths.*;

@Slf4j
@Service
public class FileService {
    private final MinioClient minioClient;

    @Autowired
    public FileService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Value("${minio.bucket-name}")
    private String bucketName;

    public FileDtoResponse info(Long userId, String rawPath) {
        String path = normalize(rawPath);
        if (path.isEmpty()) {
            return new FileDtoResponse("/", "", Type.DIRECTORY);
        }
        String name = extractName(path);
        String parent = parentPath(path);
        String objectName = buildObjectName(userId, path);
        if (isDirectory(path)) {
            if (!directoryExists(path)) {
                throw new NotFoundException("Directory not found: " + path);
            }
            return new FileDtoResponse(parent, name, Type.DIRECTORY);
        } else {
            StatObjectResponse stat = statObject(objectName, path);
            return new FileDtoResponse(parent, name, stat.size(), Type.FILE);
        }
    }

    public void delete(Long userId, String rawPath) {
        String path = normalize(rawPath);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete root directory");
        }
        String objectName = buildObjectName(userId, path);
        if (!isDirectory(path)) {
            statObject(objectName, path);
            removeObject(objectName);
        } else {
            if (!directoryExists(objectName)) {
                throw new NotFoundException("Directory not found:" + path);
            }
            removeDirectory(objectName);
        }
    }

    public void download(Long userId, String rawPath, OutputStream outputStream) {
        String path = normalize(rawPath);
        if (isDirectory(path)) {
            downloadDirectory(userId, path, outputStream);

        } else {
            downloadFile(userId, path, outputStream);
        }
    }

    public FileDtoResponse move(Long userId, String rawFrom, String rawTo) {
        String from = normalize(rawFrom);
        String to = normalize(rawTo);
        String fromObjectName = buildObjectName(userId, from);
        String toObjectName = buildObjectName(userId, to);
        statObject(fromObjectName, from);
        if (objectExists(toObjectName)) {
            throw new ConflictException("Resource already exists: " + to);
        }
        if (isDirectory(from)) {
            moveDirectory(fromObjectName, toObjectName);
            return new FileDtoResponse(parentPath(to), extractName(to), Type.DIRECTORY);
        } else {
            String parentPath = parentPath(to);
            if (!parentPath.isEmpty()) {
                String parentObject = buildObjectName(userId, parentPath);
                if (!directoryExists(parentObject)) {
                    throw new NotFoundException("Parent directory not found: " + parentPath);
                }
            }
            moveObject(fromObjectName, toObjectName);
            StatObjectResponse stat = statObject(toObjectName, to);
            return new FileDtoResponse(parentPath(to), extractName(to), stat.size(), Type.FILE);
        }
    }

    public List<FileDtoResponse> search(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        String prefix = buildObjectName(userId, "");
        List<FileDtoResponse> result = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> r : objects) {
                Item item = r.get();
                String relative = item.objectName().substring(prefix.length());
                if (relative.isEmpty()) continue;
                String name = extractName(relative);
                if (!name.toLowerCase().contains(query.toLowerCase())) continue;
                if (isDirectory(relative)) {
                    result.add(new FileDtoResponse(parentPath(relative), name, Type.DIRECTORY));
                } else {
                    result.add(new FileDtoResponse(parentPath(relative), name, item.size(), Type.FILE));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
        return result;
    }

    public List<FileDtoResponse> upload(Long userId, String rawPath, List<MultipartFile> files)  {
        String path = normalize(rawPath);
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }
        List<FileDtoResponse> result = new ArrayList<>();
        if (!path.isEmpty()) {
            String parentKey = buildObjectName(userId, path);
            if (!directoryExists(parentKey)) {
                throw new NotFoundException("Parent directory not found: " + path);
            }
        }
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("File name cannot be empty");
            }
            String fullPath = path + fileName;
            String objectName = buildObjectName(userId, fullPath);
            if (objectExists(objectName)) {
                throw new ConflictException("File already exists: " + fullPath);
            }
            String[] parts = fullPath.split("/");
            String currentPath = "";
            for (int i = 0; i < parts.length - 1; i++) {
                currentPath += parts[i] + "/";
                String markerObject = buildObjectName(userId, currentPath);
                if (!directoryExists(markerObject)) {
                    putEmptyObject(markerObject);
                }
            }
            try (InputStream inputStream = file.getInputStream()) {
                String contentType = file.getContentType() != null
                        ? file.getContentType()
                        : "application/octet-stream";
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException("MinIO error: " + e.getMessage(), e);
            }
            result.add(new FileDtoResponse(
                    parentPath(fullPath),
                    extractName(fullPath),
                    file.getSize(),
                    Type.FILE
            ));
        }
        return result;
    }

    public FileDtoResponse createDirectory(Long userId, String rawPath) {
        String path = normalize(rawPath);
        if (!isDirectory(path)) {
            throw new IllegalArgumentException("Directory path must end with /");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Cannot create root directory");
        }
        String objectName = buildObjectName(userId, path);
        if (directoryExists(objectName)) {
            throw new ConflictException("Directory already exists: " + path);
        }
        String parentPath = parentPath(path);
        if (!parentPath.isEmpty()) {
            String parentObject = buildObjectName(userId, parentPath);
            if (!directoryExists(parentObject)) {
                throw new NotFoundException("Parent directory not found: " + parentPath);
            }
        }
        putEmptyObject(objectName);
        return new FileDtoResponse(
                parentPath(path),
                extractName(path),
                Type.DIRECTORY
        );
    }

    public List<FileDtoResponse> getDirectories(Long userId, String rawPath) {
        String path = normalize(rawPath);
        if (!isDirectory(path)) {
            throw new IllegalArgumentException("Directory path must end with /");
        }
        String prefix = buildObjectName(userId, path);
        if (!path.isEmpty() && !directoryExists(prefix)) {
            throw new NotFoundException("Directory not found: " + path);
        }
        List<FileDtoResponse> result = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(false)
                            .build()
            );
            for (Result<Item> r : objects) {
                Item item = r.get();
                String objectName = item.objectName();
                String relative = objectName.substring(prefix.length());
                if (relative.isEmpty()) continue;
                String name = isDirectory(relative) ? relative.substring(0, relative.length() - 1) : relative;

                if (isDirectory(relative)) {
                    result.add(new FileDtoResponse(path, name, Type.DIRECTORY));
                } else {
                    result.add(new FileDtoResponse(path, name, item.size(), Type.FILE));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
        return result;
    }

    protected void downloadDirectory(Long userId, String path, OutputStream outputStream) {
        String prefix = buildObjectName(userId, path);
        if (!directoryExists(prefix)) {
            throw new NotFoundException("Directory not found: " + path);
        }
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            Iterable<Result<Item>> object = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> r : object) {
                Item item = r.get();
                String objectName = item.objectName();
                String relative = objectName.substring(prefix.length());
                if (relative.isEmpty()) continue;
                if (relative.endsWith("/")) {
                    ZipEntry entry = new ZipEntry(relative);
                    entry.setSize(0);
                    zipOutputStream.putNextEntry(entry);
                    zipOutputStream.closeEntry();
                    continue;
                }
                try (InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build())) {
                    ZipEntry entry = new ZipEntry(relative);
                    entry.setSize(item.size());
                    zipOutputStream.putNextEntry(entry);
                    inputStream.transferTo(zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Minio error" + e.getMessage());
        }
    }

    protected void downloadFile(Long userId, String path, OutputStream outputStream) {
        String objectName = buildObjectName(userId, path);
        statObject(objectName, path);
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {
            inputStream.transferTo(outputStream);
        } catch (Exception e) {
            throw new RuntimeException("MinIO error", e);
        }
    }

    protected void moveDirectory(String fromObjectName, String toObjectName) {
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(fromObjectName)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                String fromObject = item.objectName();
                String suffix = fromObject.substring(fromObjectName.length());
                String toObject = toObjectName + suffix;
                moveObject(fromObject, toObject);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void moveObject(String fromObjectName, String toObjectName) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(toObjectName)
                            .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(fromObjectName)
                                    .build()
                            )
                            .build()
            );
            removeObject(fromObjectName);
        } catch (Exception e) {
            throw new RuntimeException("MinIO error", e);
        }
    }

    protected boolean objectExists(String objectName) {
        try {
            statObject(objectName, objectName);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    protected void removeObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
    }

    protected void removeDirectory(String objectName) {
        try {
            Iterable<Result<Item>> object = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(objectName)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : object) {

                Item item = result.get();
                removeObject(item.objectName());
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
    }

    protected boolean directoryExists(String prefix) {
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .maxKeys(1)
                            .build()
            );
            return objects.iterator().hasNext();
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
    }
    protected void putEmptyObject(String objectName) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
    }

    protected StatObjectResponse statObject(String objectName, String path) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            throw new NotFoundException("Resource not found: " + path);
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
    }
}
