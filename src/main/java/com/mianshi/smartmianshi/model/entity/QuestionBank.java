package com.mianshi.smartmianshi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

import lombok.Data;

/**
 * 题库
 *
 * @TableName question_bank
 */
@TableName(value = "question_bank")
@Data
public class QuestionBank {
    /**
     * id
     */
    //@TableId(type = IdType.AUTO)递增生成容易被爬虫抓取
    @TableId(type = IdType.ASSIGN_ID)//雪花算法生成id
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    /*
    MyBatis-Plus 框架提供的注解，用于标识数据库表中的逻辑删除字段。
    当实体类字段添加此注解后，框架会自动处理逻辑删除操作，将删除操作转换为更新该字段的值（如设置为1表示已删除），
    而不是真正从数据库中物理删除记录
     */
    @TableLogic
    private Integer isDelete;
}