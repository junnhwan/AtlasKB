package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.ChunkInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkInfoRepository extends JpaRepository<ChunkInfo, Long> {
}
