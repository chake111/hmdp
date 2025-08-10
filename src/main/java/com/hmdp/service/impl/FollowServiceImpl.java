package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate redisTemplate;

    private final IUserService userService;

    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER_KEY + userId;
        // 2. 判断是否已经关注
        if (isFollow) {
            // 3. 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = this.save(follow);
            if (isSuccess) {
                // 把关注用户的id存入redis
                redisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 4. 取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 从redis中删除关注用户的id
                redisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        // 5. 返回结果
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        return Result.ok(count() > 0);
    }

    @Override
    public Result followCommons(Long followUserId) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER_KEY + userId;
        // 2.求交集
        String key2 = FOLLOW_USER_KEY + followUserId;
        Set<String> intersect = redisTemplate.opsForSet().intersect(key, key2);
        // 3. 解析id集合
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .toList();
        // 4. 查询用户
        List<User> userList = userService.listByIds(ids);
        List<User> userDTOList = userList.stream()
                .map(u -> BeanUtil.copyProperties(u, User.class))
                .toList();
        return Result.ok(userDTOList);
    }
}
