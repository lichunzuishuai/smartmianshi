package com.mianshi.smartmianshi.model.dto.questionBank;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建题库请求
 */
@Data
public class QuestionBankAddRequest implements Serializable {

    /**
     * 标题
     */
    private String title;
    /**
     * 图片
     */
    private String picture;
    /**
     * 描述
     */
    private String description;

    private static final long serialVersionUID = 1L;
}