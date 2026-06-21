package com.fastrag.module.iam.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*;
import com.fastrag.module.iam.service.AuthService; import com.fastrag.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login") public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest r) { return ApiResponse.success(authService.login(r)); }
    @GetMapping("/userinfo") public ApiResponse<LoginResponse.UserInfoDto> info() { return ApiResponse.success(authService.getUserInfo(SecurityUtil.getCurrentUserId())); }
    @PostMapping("/logout") public ApiResponse<Void> logout(HttpServletRequest r) { String t=r.getHeader("Authorization"); if(t!=null&&t.startsWith("Bearer "))authService.logout(t.substring(7)); return ApiResponse.success(); }
}
