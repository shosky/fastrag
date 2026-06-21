package com.fastrag.module.publish.service;
import com.fastrag.module.publish.entity.KbVersion; import java.util.*;
public interface PublishService { List<KbVersion> listVersions(String kbId); KbVersion getLatestVersion(String kbId); KbVersion getPublishedVersion(String kbId); KbVersion createVersion(String kbId,KbVersion data); void transitionStatus(String kbId,String versionId,String targetStatus); }
