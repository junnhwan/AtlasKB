package io.hwan.atlaskb.document.repository;

import io.hwan.atlaskb.document.entity.DocumentVector;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVectorRepository extends JpaRepository<DocumentVector, Long> {
}
