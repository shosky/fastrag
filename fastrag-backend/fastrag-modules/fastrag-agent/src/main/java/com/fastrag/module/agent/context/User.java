package com.fastrag.module.agent.context;

import lombok.Data;

import java.util.List;

/**
 * Agent模块用户数据模型，表示当前登录用户的精简信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>作为Agent模块内部传递用户身份信息的轻量级POJO</li>
 *   <li>由{@link com.fastrag.module.agent.config.CurrentUserArgumentResolver}从Spring Security的LoginUser转换而来</li>
 *   <li>通过{@link CurrentUser}注解注入到Controller方法参数中</li>
 * </ul></p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>uid - 用户唯一标识</li>
 *   <li>username - 用户名</li>
 *   <li>role - 用户角色（取第一个角色，默认"user"）</li>
 *   <li>departmentIds - 用户所属部门ID列表（当前为预留字段，默认空列表）</li>
 * </ul></p>
 *
 * @see CurrentUser 用于注入此对象的注解
 * @see com.fastrag.module.agent.config.CurrentUserArgumentResolver 创建此对象的解析器
 */
@Data
public class User {

    private String uid;

    private String role;

    private String username;

    private List<String> departmentIds;
}
