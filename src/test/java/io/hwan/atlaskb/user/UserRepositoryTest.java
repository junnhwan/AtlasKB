package io.hwan.atlaskb.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.organization.entity.OrganizationTag;
import io.hwan.atlaskb.organization.repository.OrganizationTagRepository;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Test
    void savesUserAndOrganizationTag() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("encoded-password");
        user.setRole("ADMIN");
        user.setOrgTags("default,admin");
        user.setPrimaryOrg("default");

        User savedUser = userRepository.save(user);

        OrganizationTag orgTag = new OrganizationTag();
        orgTag.setTagId("default");
        orgTag.setName("Default");
        orgTag.setDescription("Default organization");
        orgTag.setCreatedBy(savedUser.getId());

        organizationTagRepository.save(orgTag);

        assertTrue(userRepository.findByUsername("admin").isPresent());
        assertEquals("default", userRepository.findByUsername("admin").orElseThrow().getPrimaryOrg());
        assertEquals(1L, organizationTagRepository.count());
    }
}
