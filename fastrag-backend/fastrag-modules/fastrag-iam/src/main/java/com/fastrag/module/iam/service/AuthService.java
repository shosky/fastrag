package com.fastrag.module.iam.service;
import com.fastrag.module.iam.model.*;
public interface AuthService { LoginResponse login(LoginRequest req); LoginResponse.UserInfoDto getUserInfo(String userId); void logout(String token); }
