package org.project.cloud.file.service;

import io.minio.*;

import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.exception.ConflictException;
import org.project.cloud.exception.NotFoundException;
import org.project.cloud.file.model.Type;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.minio.errors.ErrorResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        log.info("validation path");
        if (path.equals("/")) {
            log.error("Not info, its rooting directory");
            return new FileDtoResponse("/", "", Type.DIRECTORY);
        }
        String name = extractName(path);
        log.info("extract name", name);
        String parentPath = extractPath(path);
        log.info("extract parentPath", parentPath);
        String objectName = buildObjectName(userId, path);
        log.info("extract object name",objectName);
        if (isDirectory(path)) {
            if (!directoryExists(objectName)) {
                throw new NotFoundException("Directory not found: " + path);
            }
            return new FileDtoResponse(parentPath, name, Type.DIRECTORY);
        } else {
            StatObjectResponse stat = statObject(objectName, path);
            return new FileDtoResponse(parentPath, name, stat.size(), Type.FILE);
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

    public void download(Long userId, String path, OutputStream outputStream) {
        validatePath(path);
        if (isDirectory(path)) {
            downloadDirectory(userId, path, outputStream);

        } else {
            downloadFile(userId, path, outputStream);
        }
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
            return new FileDtoResponse(extractPath(to), extractName(to), Type.DIRECTORY);
        } else {
            moveObject(fromObjectName, toObjectName);
            StatObjectResponse stat = statObject(toObjectName, to);
            return new FileDtoResponse(extractPath(to), extractName(to), stat.size(), Type.FILE);
        }
    }

    public List<FileDtoResponse> search(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        String prefix = buildObjectName(userId, "/");
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
                String objectName = item.objectName();
                String relative = objectName.substring(prefix.length());
                if (relative.isEmpty()) continue;
                String name = extractName(relative);
                if (!name.toLowerCase().contains(query.toLowerCase())) continue;
                String path = extractPath(relative);
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

    public List<FileDtoResponse> upload(Long userId, String path, List<MultipartFile> files) {
        validatePath(path);
        List<FileDtoResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
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
            result.add(new FileDtoResponse(
                    extractPath(fullPath),
                    extractName(fullPath),
                    file.getSize(),
                    Type.FILE
            ));
        }
        return result;
    }

    public FileDtoResponse createDirectory(Long userId, String path) {
        validatePath(path);
        log.info("validat path");
        if (!path.endsWith("/")) {
            log.error("path not endWish slash");
            throw new IllegalArgumentException("Directory path must end with /");
        }
        if (path.equals("/")) {
            log.error("cannot create root directory");
            throw new IllegalArgumentException("Cannot create root directory");
        }
        String objectName = buildObjectName(userId, path);
        log.info("extract object name", objectName);
        if (directoryExists(objectName)) {
            log.error("directory is already exist", objectName);
            throw new ConflictException("Directory already exists: " + path);
        }
        String parentPath = extractPath(path);
        log.info("extract parentPath", parentPath);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );
            log.info("minio create new object");
        } catch (Exception e) {
            log.error("minio not create new object");
            throw new RuntimeException("MinIO error: " + e.getMessage(), e);
        }
        return new FileDtoResponse(parentPath, extractName(path), Type.DIRECTORY);
    }

    public List<FileDtoResponse> getDirectories(Long userId, String path) {
        validatePath(path);
        if (!path.endsWith("/")) {
            throw new IllegalArgumentException("Directory path must end with /");
        }
        String prefix = buildObjectName(userId, path);
        if (!directoryExists(prefix)) {
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
    public String extractName(String path) {
        String cleanPath = path;
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        int indexSlash = cleanPath.lastIndexOf('/');
        if (indexSlash > -1) {
            return cleanPath.substring(indexSlash + 1);
        }
        return cleanPath;
    }
    public boolean isDirectory(String path) {
        return path.endsWith("/");
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
                if (relative.isEmpty() || relative.endsWith("/")) continue;
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

    protected void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null and empty");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Path cannot contain '..'");
        }
    }

    protected String extractPath(String path) {
        String cleanPath = path;
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        int indexSlash = cleanPath.lastIndexOf('/');
        if (indexSlash > -1) {
            return cleanPath.substring(0, indexSlash + 1);
        }
        return "/";
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

    protected String buildObjectName(Long userId, String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        return String.format("user-%d-files/%s", userId, cleanPath);
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
