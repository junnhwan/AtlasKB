package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.ChunkInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkInfoRepository extends JpaRepository<ChunkInfo, Long> {

    Optional<ChunkInfo> findByFileMd5AndChunkIndex(String fileMd5, Integer chunkIndex);

    List<ChunkInfo> findByFileMd5OrderByChunkIndexAsc(String fileMd5);
}
