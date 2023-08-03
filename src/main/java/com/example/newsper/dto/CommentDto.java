package com.example.newsper.dto;

import com.example.newsper.entity.CommentEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;

@AllArgsConstructor
@ToString
@Getter
public class CommentDto {
    private Long commentId;
    @JsonProperty("article_id")
    private Long articleId;
    private String userId;
    private String text;
    private Date createdAt;
    private Date modifiedAt;

    public CommentEntity toEntity(){
        return new CommentEntity(commentId,articleId,userId,text,createdAt,modifiedAt);
    }

    public static CommentDto createCommentDto(CommentEntity comment) {
        return new CommentDto(
                comment.getCommentId(),
                comment.getArticleId(),
                comment.getUserId(),
                comment.getText(),
                comment.getCreatedAt(),
                comment.getModifiedAt()
        );
    }
}
