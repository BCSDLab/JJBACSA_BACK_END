package com.jjbacsa.jjbacsabackend.follow.repository;

import com.jjbacsa.jjbacsabackend.follow.entity.FollowEntity;
import com.jjbacsa.jjbacsabackend.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, Long> {

    Optional<FollowEntity> findByUserAndFollower(UserEntity user, UserEntity follower);

    // Todo: 페이지네이션
    List<FollowEntity> findAllByUser(UserEntity user);

    boolean existsByUserAndFollower(UserEntity user, UserEntity follower);
}