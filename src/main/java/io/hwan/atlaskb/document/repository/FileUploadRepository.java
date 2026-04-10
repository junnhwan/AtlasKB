package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.FileUpload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {

    Optional<FileUpload> findByFileMd5(String fileMd5);

    Optional<FileUpload> findByFileMd5AndUserId(String fileMd5, String userId);

    List<FileUpload> findByFileMd5In(List<String> fileMd5List);

    @Query("""
            SELECT f FROM FileUpload f
            WHERE f.userId = :userId
               OR f.isPublic = true
               OR (f.orgTag IN :orgTags AND f.isPublic = false)
            ORDER BY f.createdAt DESC
            """)
    List<FileUpload> findAccessibleFilesOrderByCreatedAtDesc(
            @Param("userId") String userId,
            @Param("orgTags") List<String> orgTags
    );

    List<FileUpload> findByUserIdOrIsPublicTrueOrderByCreatedAtDesc(String userId);

    List<FileUpload> findByUserIdOrderByCreatedAtDesc(String userId);
}
