package com.evops.gradereview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.gradereview.entity.ReviewNote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewNoteMapper extends BaseMapper<ReviewNote> {
}
