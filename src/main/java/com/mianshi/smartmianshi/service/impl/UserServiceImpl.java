package com.mianshi.smartmianshi.service.impl;

import static com.mianshi.smartmianshi.constant.UserConstant.USER_LOGIN_STATE;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mianshi.smartmianshi.common.ErrorCode;
import com.mianshi.smartmianshi.config.RedissonConfig;
import com.mianshi.smartmianshi.constant.CommonConstant;
import com.mianshi.smartmianshi.constant.RedisConstant;
import com.mianshi.smartmianshi.exception.BusinessException;
import com.mianshi.smartmianshi.mapper.UserMapper;
import com.mianshi.smartmianshi.model.dto.user.UserQueryRequest;
import com.mianshi.smartmianshi.model.entity.User;
import com.mianshi.smartmianshi.model.enums.UserRoleEnum;
import com.mianshi.smartmianshi.model.vo.LoginUserVO;
import com.mianshi.smartmianshi.model.vo.UserVO;
import com.mianshi.smartmianshi.service.UserService;
import com.mianshi.smartmianshi.utils.SqlUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户服务实现
 *
 
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private RedissonClient redissonClient;

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "mianshi";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, HttpServletRequest request) {
        String unionId = wxOAuth2UserInfo.getUnionId();
        String mpOpenId = wxOAuth2UserInfo.getOpenid();
        // 单机锁
        synchronized (unionId.intern()) {
            // 查询用户是否已存在
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unionId", unionId);
            User user = this.getOne(queryWrapper);
            // 被封号，禁止登录
            if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封，禁止登录");
            }
            // 用户不存在则创建
            if (user == null) {
                user = new User();
                user.setUnionId(unionId);
                user.setMpOpenId(mpOpenId);
                user.setUserAvatar(wxOAuth2UserInfo.getHeadImgUrl());
                user.setUserName(wxOAuth2UserInfo.getNickname());
                boolean result = this.save(user);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败");
                }
            }
            // 记录用户的登录态
            request.getSession().setAttribute(USER_LOGIN_STATE, user);
            return getLoginUserVO(user);
        }
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 添加用户签到记录
     *
     * @param userId 用户id
     * @return 当前用户是否已签到成功
     */
    @Override
    public Boolean addUserSignIn(long userId) {
        LocalDate data = LocalDate.now();
        String key = RedisConstant.getUserSignInRedisKey(data.getYear(), userId);
        // 获取 Redis 中的 BitMap(BitMap和BitSet是一回事)
        RBitSet signInBitSet = redissonClient.getBitSet(key);
        //获取当前日期是一年当中的第几天
        int offset = data.getDayOfYear();
        //查询当天有没有签到
        if (!signInBitSet.get(offset)) {
            //如果当天没有签到，则进行签到
            boolean result = signInBitSet.set(offset, true);
        }
        //当天已经签到
        return true;
    }

    /**
     * 获取用户签到记录
     *
     * @param userId 用户id
     * @param year   查询年份
     * @return 用户签到记录映射
     */
    @Override
    public List<Integer> getUserSignInRecord(Long userId, Integer year) {
        if (year == null) {
            LocalDate data = LocalDate.now();
            year = data.getYear();
        }
        String key = RedisConstant.getUserSignInRedisKey(year, userId);
        // 获取 Redis 中的 BitMap(BitMap和BitSet是一回事)
        RBitSet signBitSet = redissonClient.getBitSet(key);
        //加载BitSet到内存中，避免后续读取时发送多次请求
        BitSet bitSet = signBitSet.asBitSet();
        //统计签到的日期
        List<Integer> signInDays = new ArrayList<>();

        /**
         * 假设用户的签到情况如下（一年中的第几天）：
         * 第 5 天签到（索引 5）
         * 第 10 天签到（索引 10）
         * 第 20 天签到（索引 20）
         * BitSet 的状态类似于：000001000010000000001...
         * 执行过程：
         * index = bitSet.nextSetBit(0) → 返回 5（第一个设置为1的位）
         * index != -1 为 true，进入循环
         * signInDays.add(5) → 将 5 添加到列表
         * index = bitSet.nextSetBit(5 + 1) → 即 bitSet.nextSetBit(6) → 返回 10
         * index != -1 为 true，继续循环
         * signInDays.add(10) → 将 10 添加到列表
         * index = bitSet.nextSetBit(10 + 1) → 即 bitSet.nextSetBit(11) → 返回 20
         * index != -1 为 true，继续循环
         * signInDays.add(20) → 将 20 添加到列表
         * index = bitSet.nextSetBit(20 + 1) → 即 bitSet.nextSetBit(21) → 返回 -1
         * index != -1 为 false，退出循环
         * 所以，这段代码能够正确找到所有设置为 1 的位，不会遗漏任何签到记录。
         * 关键点在于：
         * nextSetBit(0) 查找第一个设置为 1 的位（不管它在哪个索引）
         * 在循环中，每次都从 index + 1 开始查找下一个设置为 1 的位
         * 直到找不到更多设置为 1 的位（返回 -1）为止
         * 因此，代码逻辑是正确的，可以找到所有签到记录，不会遗漏索引 0 之后的任何签到。
         */
        //从索引0开始，获取下一个被置为1的位
        int index = bitSet.nextSetBit(0);
        while (index != -1) {
            signInDays.add(index);
            index = bitSet.nextSetBit(index + 1);
        }
        return signInDays;
    }
}
