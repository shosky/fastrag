package com.fastrag.common.util;

import cn.hutool.core.util.IdUtil;

/**
 * 全局唯一ID生成器。
 *
 * <p>提供系统中所有实体类主键ID的统一生成方法。基于Hutool库的fastSimpleUUID实现，
 * 生成32位不带"-"的随机UUID字符串，用作数据库表的主键ID。
 * 被各Entity在插入时使用。</p>
 */
public class IdGenerator {
    public static String nextId() { return IdUtil.fastSimpleUUID(); }
}
