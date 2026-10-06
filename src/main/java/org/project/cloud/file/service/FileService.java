package org.project.cloud.file.service;

import io.minio.*;

import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.exception.ConflictException;
import org.project.cloud.exception.NotFoundException;
import org.project.cloud.file.model.FileType;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.minio.errors.ErrorResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

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

    public FileDtoResponse info(Long userId, String path) {
        validatePath(path);
        if (path.equals("/")) {
            return new FileDtoResponse("/", "", FileType.DIRECTORY);
        }
        String name = extractName(path);
        String parentPath = extractPath(path);
        if (isDirectory(path)) {
            return new FileDtoResponse(parentPath, name, FileType.DIRECTORY);
        } else {
            String objectName = buildObjectName(userId, path);
            StatObjectResponse stat = statObject(objectName, path);
            return new FileDtoResponse(parentPath, name, stat.size(), FileType.FILE);
        }
    }

    public void delete(Long userId, String path) {
        validatePath(path);
        String objectName = buildObjectName(userId, path);
        if (!isDirectory(path)) {
            statObject(objectName, path);
            removeObject(objectName);
        } else {
            removeDirectory(objectName);
        }
    }

    public InputStream download(Long userId, String path) {
        validatePath(path);
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("Directory download not implemented");
        }
        String objectName = buildObjectName(userId, path);
        statObject(objectName, path);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO error", e);
        }
    }

    public FileDtoResponse upload(Long userId, String path, MultipartFile file) {
        validatePath(path);
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        String fullPath = path.endsWith("/")
                ? path + fileName
                : path + "/" + fileName;
        String objectName = buildObjectName(userId, fullPath);
        if (objectExists(objectName)) {
            throw new ConflictException("File already exists: " + fullPath);
        }
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
        return new FileDtoResponse(
                path,
                fileName,
                file.getSize(),
                FileType.FILE
        );
    }

    public FileDtoResponse move(Long userId, String from, String to) {
        validatePath(from);
        validatePath(to);
        String fromObjectName = buildObjectName(userId, from);
        String toObjectName = buildObjectName(userId, to);
        statObject(fromObjectName, from);
        if (objectExists(toObjectName)) {
            throw new ConflictException("Resource already exists: " + to);
        }
        if (isDirectory(from)) {
            moveDirectory(fromObjectName, toObjectName);
        } else {
            moveObject(fromObjectName, toObjectName);
        }
        return new FileDtoResponse(extractPath(to), extractName(to),
                isDirectory(to) ? FileType.DIRECTORY : FileType.FILE);
    }

    private boolean objectExists(String objectName) {
        try {
            statObject(objectName, objectName);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private boolean directoryExists(String prefix) {
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
                String toObject = fromObjectName + suffix;
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

    protected void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null and empty");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Path cannot contain '..'");
        }
    }

    protected boolean isDirectory(String path) {
        return path.endsWith("/");
    }

    protected String extractName(String path) {
        String clean = path;
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int indexSlash = clean.lastIndexOf('/');
        if (indexSlash > -1) {
            return clean.substring(indexSlash + 1);
        }
        return clean;
    }

    protected String extractPath(String path) {
        String clean = path;
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int indexSlash = clean.lastIndexOf('/');
        if (indexSlash > -1) {
            return clean.substring(0, indexSlash + 1);
        }
        return "/";
    }

    protected String buildObjectName(Long userId, String path) {
        return String.format("user-%d-files/%s", userId, path);
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
}
