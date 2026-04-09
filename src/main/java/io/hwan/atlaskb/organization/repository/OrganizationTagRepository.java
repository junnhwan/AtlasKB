package io.hwan.atlaskb.organization.repository;

import io.hwan.atlaskb.organization.entity.OrganizationTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
}
