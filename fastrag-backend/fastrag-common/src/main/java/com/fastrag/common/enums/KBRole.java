package com.fastrag.common.enums;

/**
 * 知识库角色枚举。
 *
 * <p>定义用户在知识库中的访问角色：owner（所有者，拥有完全管理权限）、
 * editor（编辑者，可编辑内容）、viewer（查看者，仅可浏览）。
 * 用于知识库的访问权限控制。</p>
 */
public enum KBRole {
  owner, editor, viewer
}
