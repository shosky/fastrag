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
 * 当前登录用户参数解析器，将Spring Security上下文中的用户信息注入到Controller方法参数中。
 *
 * <p>核心职责：
 * <ul>
 *   <li>解析Controller方法中标注了{@link CurrentUser}注解且类型为{@link User}的参数</li>
 *   <li>从Spring Security上下文中提取当前登录的LoginUser信息，转换为Agent模块的User对象</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>supportsParameter方法判断参数是否带有@CurrentUser注解且类型为User</li>
 *   <li>resolveArgument方法通过SecurityUtil.getCurrentUser()获取安全上下文中的LoginUser</li>
 *   <li>将LoginUser的userId、username、roles映射到User对象（角色取第一个，默认"user"）</li>
 *   <li>未登录时返回null，由各Controller接口自行处理权限判断逻辑</li>
 * </ul></p>
 *
 * <p>使用方式：在Controller方法中使用 @CurrentUser User user 参数即可自动注入当前用户。</p>
 *
 * @see CurrentUser 当前用户注解
 * @see User Agent模块用户模型
 * @see AgentWebMvcConfig 注册此解析器的MVC配置类
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
