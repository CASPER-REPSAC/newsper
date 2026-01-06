package com.example.newsper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class JoinDto {

    @Schema(description = "로그인 시 사용할 계정 명")
    private String id;

    @Schema(description = "패스워드 평문")
    private String pw;

    @Schema(description = "이메일 주소 고유 값")
    private String email;

    @Schema(description = "실명")
    private String name;

    @Schema(description = "표시할 별명")
    private String nickname;

    public UserDto toUserDto() {
        return new UserDto(id, pw, email, name, nickname, null, null, null, null);
    }

    @JsonIgnore
    public boolean isValid() {
        return id != null && pw != null && email != null && name != null && nickname != null;
    }
}
