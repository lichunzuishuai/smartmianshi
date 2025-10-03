package com.mianshi.smartmianshi.mapper;

import com.mianshi.smartmianshi.model.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * @author lcs
 * @description 针对表【question(题目)】的数据库操作Mapper
 * @createDate 2025-09-14 21:10:37
 * @Entity com.mianshi.smartmianshi.model.entity.Question
 */
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 查询题目列表（包括已被删除的数据）
     *
     * @param fiveMinutesAgoDate
     * @return
     */
    @Select("select * from question where updateTime >= #{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date fiveMinutesAgoDate);
}




