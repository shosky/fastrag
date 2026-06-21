package com.fastrag.module.publish.service;
import com.fastrag.module.publish.entity.*; import java.util.*;
public interface LogService { List<KbLog> listLogs(String kbId,String category); List<KbUpdateLog> listUpdateLogs(String kbId,String type); }
