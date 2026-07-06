package com.fastrag.module.agent.config;

import com.fastrag.module.agent.context.CurrentUser;
import com.fastrag.module.agent.context.User;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.ArrayList;

/**
 * 解析控制器方法中标注了 {@link CurrentUser} 注解的参数，
 * 从 Spring Security 上下文中提取当前登录用户信息并转换为 {@link User} 对象。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().isAssignableFrom(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        try {
            LoginUser loginUser = SecurityUtil.getCurrentUser();
            User user = new User();
            user.setUid(loginUser.getUserId());
            user.setUsername(loginUser.getUsername());
            user.setDepartmentIds(new ArrayList<>());
            // role: 取第一个角色，默认 "user"
            user.setRole(loginUser.getRoles() != null && !loginUser.getRoles().isEmpty()
                    ? loginUser.getRoles().get(0) : "user");
            return user;
        } catch (Exception e) {
            // 未登录时返回 null，由各接口自行处理权限判断
            return null;
        }
    }
}
