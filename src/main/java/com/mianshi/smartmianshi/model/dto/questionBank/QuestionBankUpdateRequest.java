package com.mianshi.smartmianshi.model.dto.questionBank;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新题库请求
 */
@Data
public class QuestionBankUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

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