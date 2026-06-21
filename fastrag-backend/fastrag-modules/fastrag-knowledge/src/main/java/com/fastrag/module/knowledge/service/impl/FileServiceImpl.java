package com.fastrag.module.knowledge.service.impl;
import cn.hutool.core.io.FileUtil; import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbFile; import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.model.FileDto; import com.fastrag.module.knowledge.service.FileService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate; import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime; import java.util.*; import java.util.stream.Collectors;
@Slf4j @Service @RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final KbFileMapper fileMapper; private final RabbitTemplate rabbitTemplate;
    @Override public List<FileDto> list(String kbId) { return fileMapper.selectList(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId,kbId).isNull(KbFile::getDeletedAt).orderByDesc(KbFile::getCreatedAt)).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public List<FileDto> listDeleted(String kbId) { return fileMapper.selectList(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId,kbId).isNotNull(KbFile::getDeletedAt)).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public FileDto upload(String kbId,MultipartFile file) {
        var f=new KbFile(); f.setKbId(kbId); f.setName(file.getOriginalFilename());
        f.setExtension(FileUtil.extName(file.getOriginalFilename())); f.setSize(file.getSize());
        f.setCategory("document"); f.setStatus("pending"); f.setProgress(0); f.setChunkCount(0);
        f.setObjectKey(kbId+"/"+IdUtil.fastSimpleUUID()); fileMapper.insert(f);
        try { var msg=new HashMap<String,Object>(); msg.put("fileId",f.getId()); msg.put("kbId",kbId); msg.put("objectKey",f.getObjectKey()); rabbitTemplate.convertAndSend("ingestion.queue",msg); } catch(Exception e){log.error("MQ error",e);}
        return toDto(f);
    }
    @Override public FileDto update(String kbId,String fileId,Map<String,Object> patch) {
        var f=fileMapper.selectOne(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId,kbId).eq(KbFile::getId,fileId).isNull(KbFile::getDeletedAt));
        if(f==null)throw new RuntimeException("File not found"); if(patch.containsKey("name"))f.setName((String)patch.get("name"));
        if(patch.containsKey("folderId"))f.setFolderId((String)patch.get("folderId")); fileMapper.updateById(f); return toDto(f);
    }
    @Override public void delete(String kbId,String fileId) { var f=fileMapper.selectById(fileId); if(f!=null){f.setDeletedAt(LocalDateTime.now());fileMapper.updateById(f);} }
    @Override public void restore(String kbId,String fileId) { var f=fileMapper.selectById(fileId); if(f!=null){f.setDeletedAt(null);fileMapper.updateById(f);} }
    @Override public void permanentDelete(String kbId,String fileId) { fileMapper.deleteById(fileId); }
    @Override public void emptyRecycleBin(String kbId) { fileMapper.selectList(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId,kbId).isNotNull(KbFile::getDeletedAt)).forEach(f->fileMapper.deleteById(f.getId())); }
    @Override public FileDto copy(String kbId,String fileId) { var s=fileMapper.selectById(fileId); if(s==null)throw new RuntimeException("File not found"); var c=new KbFile(); c.setKbId(kbId); c.setName(s.getName()+" (copy)"); c.setCategory(s.getCategory()); c.setExtension(s.getExtension()); c.setSize(s.getSize()); c.setObjectKey(s.getObjectKey()); c.setStatus("pending"); c.setProgress(0); c.setChunkCount(0); fileMapper.insert(c); return toDto(c); }
    @Override public Map<String,Object> getProcessingStatus(String kbId,String fileId) { var f=fileMapper.selectById(fileId); if(f==null)throw new RuntimeException("File not found"); var r=new HashMap<String,Object>(); r.put("fileId",f.getId()); r.put("status",f.getStatus()); r.put("progress",f.getProgress()!=null?f.getProgress():0); r.put("stage",f.getStage()!=null?f.getStage():""); return r; }
    private FileDto toDto(KbFile f) { var d=new FileDto(); d.setId(f.getId()); d.setName(f.getName()); d.setCategory(f.getCategory()); d.setExtension(f.getExtension()); d.setSize(f.getSize()); d.setUrl(f.getObjectKey()); d.setStatus(f.getStatus()); d.setProgress(f.getProgress()); d.setStage(f.getStage()); d.setChunkCount(f.getChunkCount()); d.setFolderId(f.getFolderId()); d.setDeletedAt(f.getDeletedAt()); d.setCreatedAt(f.getCreatedAt()); d.setUpdatedAt(f.getUpdatedAt()); return d; }
}
