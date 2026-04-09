package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.FileUpload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {

    Optional<FileUpload> findByFileMd5AndUserId(String fileMd5, String userId);

    List<FileUpload> findByFileMd5In(List<String> fileMd5List);
}
