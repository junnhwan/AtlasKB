package io.hwan.atlaskb.user.service;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getPrimaryOrg(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(4041, "用户不存在"));
        if (!StringUtils.hasText(user.getPrimaryOrg())) {
            throw new BusinessException(4002, "用户未配置主组织");
        }
        return user.getPrimaryOrg();
    }
}
