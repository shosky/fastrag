package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.SkillFileContent;
import com.fastrag.module.tools.entity.SkillFileTree;

import java.io.File;

public interface SkillFileService {

    /** 获取技能文件树 */
    SkillFileTree getTree(String slug);

    /** 读取技能文件 */
    SkillFileContent readFile(String slug, String relativePath);

    /** 创建文件或目录 */
    void createNode(String slug, String relativePath, boolean isDir, String content, String operator);

    /** 更新文件 */
    void updateFile(String slug, String relativePath, String content, String operator);

    /** 删除文件或目录 */
    void deleteNode(String slug, String relativePath, String operator);

    /** 导出技能目录为 ZIP */
    File exportZip(String slug);
}
