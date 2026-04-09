package io.hwan.atlaskb.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgTagPermissionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void resolveAccessibleOrgTagsIncludesPrimaryOrgAndDeduplicatesTags() {
        User user = new User();
        user.setId(1L);
        user.setPrimaryOrg("default");
        user.setOrgTags("sales, default, sales,研发");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        OrgTagPermissionService permissionService = new OrgTagPermissionService(userRepository);

        List<String> tags = permissionService.resolveAccessibleOrgTags("1");

        assertEquals(List.of("default", "sales", "研发"), tags);
    }

    @Test
    void resolveAccessibleOrgTagsFallsBackToPrimaryOrgWhenOrgTagsEmpty() {
        User user = new User();
        user.setId(2L);
        user.setPrimaryOrg("default");
        user.setOrgTags("   ");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        OrgTagPermissionService permissionService = new OrgTagPermissionService(userRepository);

        List<String> tags = permissionService.resolveAccessibleOrgTags("2");

        assertEquals(List.of("default"), tags);
    }

    @Test
    void resolveAccessibleOrgTagsThrowsWhenUserMissing() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        OrgTagPermissionService permissionService = new OrgTagPermissionService(userRepository);

        assertThrows(BusinessException.class, () -> permissionService.resolveAccessibleOrgTags("9"));
    }
}
