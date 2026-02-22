package com.example.infrastructure.repository;

import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationGroupJpaRepository extends JpaRepository<NotificationGroup, Long> {

    List<NotificationGroup> findByClientId(String clientId);

    List<NotificationGroup> findByGroupType(GroupType groupType);
}
