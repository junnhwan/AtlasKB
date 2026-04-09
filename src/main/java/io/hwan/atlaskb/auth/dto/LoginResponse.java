package io.hwan.atlaskb.auth.dto;

public class LoginResponse {

    private final String token;
    private final UserInfo userInfo;

    public LoginResponse(String token, UserInfo userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }

    public String getToken() {
        return token;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public static class UserInfo {

        private final Long id;
        private final String username;
        private final String role;

        public UserInfo(Long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }
}
