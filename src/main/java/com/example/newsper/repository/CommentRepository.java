package com.example.newsper.repository;

import com.example.newsper.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    @Query(value = "SELECT * FROM commentEntity WHERE articleId = :articleId", nativeQuery = true)
    List<CommentEntity> findByArticleId(@Param("articleId") Long articleId);

    @Modifying
    @Query(value = "UPDATE commentEntity SET nickname = :nickname WHERE id = :id", nativeQuery = true)
    void changeNicknameInComment(@Param("nickname") String nickname, @Param("id") String id);
}
