package com.fastrag.module.application.widget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.application.widget.entity.Widget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WidgetMapper extends BaseMapper<Widget> {
}
