package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.DocumentVector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVectorRepository extends JpaRepository<DocumentVector, Long> {

    List<DocumentVector> findByFileMd5AndUserIdOrderByChunkIdAsc(String fileMd5, String userId);

    void deleteByFileMd5AndUserId(String fileMd5, String userId);
}
