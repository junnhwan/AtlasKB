package io.hwan.atlaskb.organization.service;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrgTagPermissionService {

    private final UserRepository userRepository;

    public OrgTagPermissionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<String> resolveAccessibleOrgTags(String userId) {
        Long parsedUserId;
        try {
            parsedUserId = Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(4002, "用户ID格式非法");
        }

        User user = userRepository.findById(parsedUserId)
                .orElseThrow(() -> new BusinessException(4041, "用户不存在"));

        Set<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(user.getPrimaryOrg())) {
            tags.add(user.getPrimaryOrg().trim());
        }

        if (StringUtils.hasText(user.getOrgTags())) {
            for (String orgTag : user.getOrgTags().split(",")) {
                String normalized = orgTag.trim();
                if (StringUtils.hasText(normalized)) {
                    tags.add(normalized);
                }
            }
        }

        return new ArrayList<>(tags);
    }
}
