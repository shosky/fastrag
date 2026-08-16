package com.fastrag.module.publish.service;

/**
 * 知识库版本发布服务接口。
 *
 * <p>定义知识库版本的生命周期管理能力，包括版本的创建、查询以及状态流转。
 * 版本状态包括 draft（草稿）、pending_review（待审核）、approved（已通过）、
 * published（已发布）、rejected（已驳回）。</p>
 *
 * @see KbVersion
 */
import com.fastrag.module.publish.entity.KbVersion; import java.util.*;
public interface PublishService { List<KbVersion> listVersions(String kbId); KbVersion getLatestVersion(String kbId); KbVersion getPublishedVersion(String kbId); KbVersion createVersion(String kbId,KbVersion data); void transitionStatus(String kbId,String versionId,String targetStatus); }
