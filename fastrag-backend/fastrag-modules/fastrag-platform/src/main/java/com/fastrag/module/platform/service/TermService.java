package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface TermService { List<TermLibrary> listLibraries(); TermLibrary createLibrary(Map<String,Object> form); void deleteLibrary(String id); List<TermRecord> listTerms(String libraryId); TermRecord createTerm(Map<String,Object> form); void deleteTerm(String id); }
