package com.example.newsper.service;

import com.example.newsper.dto.ArticleDto;
import com.example.newsper.entity.ArticleEntity;
import com.example.newsper.entity.ArticleList;
import com.example.newsper.entity.UserEntity;
import com.example.newsper.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ArticleService {

    @Autowired
    private ArticleRepository articleRepository;

    public List<ArticleList> boardList(String boardId, String category, Long listNum){
        if(category.equals("all")) return articleRepository.findByBoardListAll(boardId, listNum);
        else return articleRepository.findByBoardList(boardId,category,listNum);
    }

    public int getMaxPageNum(String boardId, String category){
        return articleRepository.findAllBoardListCount(boardId,category);
    }

    public ArticleEntity findById(Long articleId){
        return articleRepository.findById(articleId).orElse(null);
    }

    public void delete(ArticleEntity articleEntity) {
        articleRepository.delete(articleEntity);
    }

    public ArticleEntity update(Long id, ArticleDto dto) {
        ArticleEntity article = dto.toEntity();
        ArticleEntity target = articleRepository.findById(id).orElse(null);
        if (target == null || !id.equals(article.getArticleId())){
            return null;
        }
        target.patch(article);
        log.info(target.toString());
        ArticleEntity updated = articleRepository.save(target);
        return updated;
    }

    public boolean writerCheck(ArticleEntity article, UserEntity user) {
        return article.getUserId().equals(user.getId()) || user.getRole().equals("admin");
    }

    public boolean isHide(ArticleEntity article, UserEntity user) {
        if(!article.getHide()) return true;

        if(user == null) return false;
        else if(user.getRole().equals("associate")) return writerCheck(article,user);
        else return true;
    }

    public boolean authCheck(String boardId, UserEntity user) {
        if(boardId.equals("freedom_board")||boardId.equals("notice_board")) return true;
        else if(user == null) return false;
        else if(user.getRole().equals("associate")) return boardId.equals("associate_member_board");
        else return true;
    }

    public ArticleEntity write(ArticleDto dto,UserEntity user){
        dto.setUserId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setView(0L);
        dto.setNumOfComments(0L);
        Date date = new Date(System.currentTimeMillis()+3600*9*1000);
        dto.setCreatedAt(date);
        dto.setModifiedAt(date);

        ArticleEntity article = dto.toEntity();

        return articleRepository.save(article);
    }
}
