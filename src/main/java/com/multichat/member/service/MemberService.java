package com.multichat.member.service;

import java.util.UUID;

public interface MemberService {
    void join(UUID roomId, UUID userId);
    void leave(UUID roomId, UUID userId);
}
