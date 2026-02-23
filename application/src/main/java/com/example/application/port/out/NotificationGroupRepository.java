package com.example.application.port.out;

import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;

import java.util.List;
import java.util.Optional;

public interface NotificationGroupRepository {

    NotificationGroup save(NotificationGroup group);

    Optional<NotificationGroup> findById(Long id);

    Optional<NotificationGroup> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    List<NotificationGroup> findByClientId(String clientId);

    List<NotificationGroup> findByGroupType(GroupType groupType);

    void delete(NotificationGroup group);
}
