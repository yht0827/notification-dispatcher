package com.example.infrastructure.repository;

import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationGroupRepositoryImpl implements NotificationGroupRepository {

    private final NotificationGroupJpaRepository jpaRepository;

    @Override
    public NotificationGroup save(NotificationGroup group) {
        return jpaRepository.save(group);
    }

    @Override
    public Optional<NotificationGroup> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<NotificationGroup> findByClientId(String clientId) {
        return jpaRepository.findByClientId(clientId);
    }

    @Override
    public List<NotificationGroup> findByGroupType(GroupType groupType) {
        return jpaRepository.findByGroupType(groupType);
    }

    @Override
    public void delete(NotificationGroup group) {
        jpaRepository.delete(group);
    }
}
