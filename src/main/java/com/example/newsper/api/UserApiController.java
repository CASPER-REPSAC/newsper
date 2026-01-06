package com.example.newsper.api;

import com.example.newsper.annotation.AdminOnly;
import com.example.newsper.annotation.Authorized;
import com.example.newsper.annotation.Unauthorized;
import com.example.newsper.constant.ErrorCode;
import com.example.newsper.constant.UserRole;
import com.example.newsper.dto.*;
import com.example.newsper.entity.FileEntity;
import com.example.newsper.entity.UserEntity;
import com.example.newsper.util.JwtTokenUtil;
import com.example.newsper.util.RedisUtil;
import com.example.newsper.service.*;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Tag(name = "User", description = "유저 API")
@RestController
@Slf4j
@RequestMapping("/api/user")
public class UserApiController {

    @Value("${custom.secret-key}")
    String secretKey;
    @Value("${custom.debug}")
    boolean debug;
    @Autowired
    private FileService fileService;
    @Autowired
    private UserService userService;
    @Autowired
    private ErrorCodeService errorCodeService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MailService mailService;
    @Autowired
    private AccountLockService accountLockService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private OAuthService oAuthService;
    @Hidden
    @GetMapping("/create_admin")
    @PermitAll
    @Operation(summary = "관리자 생성", description = "관리자 계정을 생성합니다. Debug 모드에서만 사용 가능합니다.")
    public ResponseEntity<?> createAdmin() {
        if(!debug) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.USABLE_ONLY_DEVELOPMENT));
        UserEntity user = userService.findById("admin");
        if (user != null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorCodeService.setErrorCodeBody(ErrorCode.SIGNUP_DUPLICATE_ID));

        UserDto userDto = new UserDto();
        userDto.setId("admin");
        userDto.setPw(secretKey);
        userDto.setRole(UserRole.ADMIN.getRole());
        userDto.setEmail("casper.cwnu@gmail.com");
        userDto.setName("관리자");
        userDto.setNickname("관리자");
        userDto.setHomepage("https://casper.or.kr");
        userDto.setIntroduce("관리자 계정입니다.");
        userDto.setProfileImgPath("/");
        userService.newUser(userDto);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/join")
    @Unauthorized
    @Operation(summary = "회원 가입", description = "DB에 회원 정보를 등록합니다.")
    public ResponseEntity<?> join(@RequestBody JoinDto dto) {
        UserEntity user = userService.findById(dto.getId());
        UserDto userDto = dto.toUserDto();

        if (!dto.isValid())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.SIGNUP_MISSING_PARAMETER));
        if (user != null || dto.getId().equals("guest"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.SIGNUP_DUPLICATE_ID));

        userService.newUser(userDto);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/pwupdate")
    @Authorized
    @Operation(summary = "비밀번호 업데이트", description = "액세스 토큰 필요.")
    public ResponseEntity<?> pwReset(@Parameter(description = "새로운 비밀번호") @RequestParam String pw, HttpServletRequest request) {
        String userId = userService.getUserId(request);
        UserEntity user = userService.findById(userId);
        user.setPw(passwordEncoder.encode(pw));
        userService.modify(user);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/findid")
    @Unauthorized
    @Operation(summary = "ID 찾기", description = "가입시 사용된 메일로 아이디를 찾습니다.")
    public ResponseEntity<?> findid(@RequestBody findIdDto dto) {
        UserEntity user = userService.findByEmail(dto.getEmail());
        if (user == null || !user.getEmail().equals(dto.getEmail()))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorCodeService.setErrorCodeBody(ErrorCode.ACCOUNT_NOT_FOUND));
        mailService.idMail(dto.getEmail(), user.getId());

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/findpw")
    @Unauthorized
    @Operation(summary = "비밀번호 찾기", description = "가입시 사용된 메일로 초기화된 비밀번호를 전송합니다.")
    public ResponseEntity<?> findpw(@RequestBody findIdDto dto) {
        UserEntity user = userService.findByEmail(dto.getEmail());
        if (user == null || !user.getEmail().equals(dto.getEmail()))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorCodeService.setErrorCodeBody(ErrorCode.ACCOUNT_NOT_FOUND));

        mailService.pwMail(user);

        return ResponseEntity.status(HttpStatus.OK).build();
    }


    @PostMapping("/update")
    @Authorized
    @Operation(summary = "유저 정보 수정", description = "닉네임, 홈페이지 주소, 소개글을 수정 합니다. 액세스 토큰 필요.")
    public ResponseEntity<UserEntity> update(@RequestBody UserModifyDto dto, HttpServletRequest request) throws IOException {
        String userId = userService.getUserId(request);
        UserEntity userEntity = userService.findById(userId);
        if(userEntity == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 관리자가 다른 유저의 정보를 수정
        if(userEntity.getRole() == UserRole.ADMIN && (dto.getId() != null)) {
            userEntity = userService.findById(dto.getId());
        }
        if (!dto.getNickname().equals(userEntity.getNickname())) {
            userEntity.setNickname(dto.getNickname());
            userService.changeNickname(userEntity);
        }

        userEntity.setHomepage(dto.getHomepage());
        userEntity.setIntroduce(dto.getIntroduce());
        userEntity.setName(dto.getName());

        if (dto.getProfileImgPath() != null) {
            if (userEntity.getProfileImgPath() != null) fileService.delete(userEntity.getProfileImgPath());

            FileEntity fileEntity = fileService.findById(dto.getProfileImgPath());
            fileEntity.setConnectId(String.valueOf(userEntity.getId()));
            fileService.modify(fileEntity);

            userEntity.setProfileImgPath(dto.getProfileImgPath());
        }
        userService.modify(userEntity);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/withdrawal/{id}")
    @Authorized
    @Operation(summary = "회원 탈퇴", description = "본인, 관리자만 탈퇴 진행 가능합니다. 액세스 토큰 필요.")
    public ResponseEntity<UserEntity> userWithdrawal(@Parameter(description = "유저 ID") @PathVariable String id, HttpServletRequest request) {
        String userId = userService.getUserId(request);
        UserEntity target = userService.findById(userId);

        if (target.getRole() == UserRole.ADMIN) userService.delete(id);
        else if (target.getId().equals(id)) userService.delete(target.getId());
        else return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();


        if (target.getProfileImgPath() != null) {
            fileService.delete(target.getProfileImgPath());
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/login")
    @Unauthorized
    @Operation(summary = "로그인", description = "로그인 토큰을 반환합니다.")
    public ResponseEntity<?> login(@RequestBody LoginDto dto, HttpServletResponse response) {
        UserEntity user = userService.findById(dto.getId());

        if (accountLockService.validation(dto.getId()))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.LOGIN_EXCEED_ATTEMPTS));

        // 로그인 아이디나 비밀번호가 틀린 경우 global error return
        if (user == null) {
            accountLockService.setCount(dto.getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.LOGIN_ID_NOT_FOUND));
        }
        if (!(passwordEncoder.matches(dto.getPw(), user.getPw()))) {
            accountLockService.setCount(dto.getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorCodeService.setErrorCodeBody(ErrorCode.LOGIN_PASSWORD_MISMATCH));
        }

        accountLockService.deleteCount(dto.getId());

        return ResponseEntity.status(HttpStatus.OK).body(userService.login(user, response));
    }

    @PostMapping("/google")
    @Unauthorized
    @Operation(summary = "구글 로그인", description = "OAuth2를 사용하여 로그인 합니다.")
    public ResponseEntity<?> googleLogin(@RequestBody OauthDto dto, HttpServletResponse response) {
        UserEntity user = oAuthService.google(dto.getCode());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorCodeService.setErrorCodeBody(ErrorCode.DISABLED_FEATURE));
        }
        return ResponseEntity.status(HttpStatus.OK).body(userService.login(user, response));
    }

    @PostMapping("/github")
    @Unauthorized
    @Operation(summary = "깃허브 로그인", description = "OAuth2를 사용하여 로그인 합니다.")
    public ResponseEntity<?> githubLogin(@RequestBody OauthDto dto, HttpServletResponse response) {
        UserEntity user = oAuthService.github(dto.getCode());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorCodeService.setErrorCodeBody(ErrorCode.DISABLED_FEATURE));
        }
        return ResponseEntity.status(HttpStatus.OK).body(userService.login(user, response));
    }

    @PostMapping("/sso")
    @Unauthorized
    @Operation(summary = "SSO 로그인", description = "OAuth2를 사용하여 로그인 합니다.")
    public ResponseEntity<?> ssoLogin(@RequestBody OauthDto dto, HttpServletResponse response) {
        UserEntity user = oAuthService.sso(dto.getCode());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorCodeService.setErrorCodeBody(ErrorCode.DISABLED_FEATURE));
        }
        return ResponseEntity.status(HttpStatus.OK).body(userService.login(user, response));
    }

    @PostMapping("/logout")
    @Authorized
    @Operation(summary = "로그아웃", description = "유저 토큰과 쿠키를 제거합니다. 액세스 토큰 필요.")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String userId = userService.getUserId(request);
        UserEntity user = userService.findById(userId);

        userService.logout(user, response);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/refresh")
    @PermitAll
    @Operation(summary = "리프레쉬", description = "유저 토큰과 쿠키를 재설정합니다. 리프레시 토큰 필요.")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            for (Cookie c : Objects.requireNonNull(cookies)) {
                if (c.getName().equals("refreshToken") && !JwtTokenUtil.isExpired(c.getValue(), secretKey)) {
                    String id = JwtTokenUtil.getLoginId(c.getValue(), secretKey);
                    UserEntity user = userService.findById(id);
                    return ResponseEntity.status(HttpStatus.OK).body(userService.login(user, response));
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/me")
    @Authorized
    @Operation(summary = "내 정보 조회", description = "내 정보를 조회합니다.")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        String userId = userService.getUserId(request);
        UserEntity user = userService.findById(userId);
        if(user == null) {
            log.error("User not found({})", userId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.status(HttpStatus.OK).body(user.toJSON());
    }

    @GetMapping("/show")
    @Authorized
    @Operation(summary = "유저 정보 조회", description = "유저 정보를 조회합니다.")
    public ResponseEntity<Map<String, Object>> show(@Parameter(description = "유저 ID") @RequestParam String id) {
        UserEntity user = userService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(user.toJSON());
    }

    @GetMapping("/showall")
    @PermitAll
    @Operation(summary = "유저 그룹 조회", description = "유저 그룹을 조회합니다.")
    public ResponseEntity<Map<String, Object>> showall(@Parameter(description = "associate, active, rest, graduate") @RequestParam String role) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.showall(UserRole.valueOfRole(role)));
    }

    @PostMapping("/auth")
    @AdminOnly
    @Operation(summary = "유저 권한 수정", description = "유저의 권한을 수정합니다. 관리자만 가능합니다.")
    public ResponseEntity<?> auth(HttpServletRequest request, @Parameter(description = "associate, active, rest, graduate") @RequestBody RoleDto dto) {
        String userId = userService.getUserId(request);
        UserEntity userEntity = userService.findById(userId);
        UserEntity distUser = userService.findById(dto.getId());

        if (distUser.getRole() == UserRole.ADMIN)
            return ResponseEntity.status((HttpStatus.BAD_REQUEST)).body(errorCodeService.setErrorCodeBody(ErrorCode.ADMIN_PERMISSION_UNCHANGEABLE));
        if (userEntity.getRole() != UserRole.ADMIN) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (dto.getId().equals("admin")) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        userService.roleChange(distUser, dto.getRole());

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
