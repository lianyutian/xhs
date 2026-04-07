package com.lianyutian.xhs.user.service.auth;

public record CurrentUserView(Long userId, String email, String nickname) {
}
