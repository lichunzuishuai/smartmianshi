package com.mianshi.smartmianshi.constant;

/*
 * @description: redis常量
 */
public class RedisConstant {
    /*
    用户签到记录的Redis Key 前缀
     */
    static String USER_SIGN_IN_REDIS_KEY = "user:signins";

    /**
     * 获取用户签到记录的Redis Key
     *
     * @param year   年份
     * @param userId 用户id
     * @return 拼接好的Redis Key
     */
    public static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY, year, userId);
    }

}
