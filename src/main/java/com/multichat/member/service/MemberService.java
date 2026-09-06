package com.multichat.member.service;

import com.multichat.member.entity.RoomMembership;

import java.util.UUID;

public interface MemberService {
    RoomMembership join(UUID roomId, UUID userId);
    RoomMembership leave(UUID roomId, UUID userId);
    RoomMembership approve(UUID membershipId, UUID actorId);
    RoomMembership reject(UUID membershipId, UUID actorId);
}
