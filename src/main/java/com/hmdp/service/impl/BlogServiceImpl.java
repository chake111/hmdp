package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;

    private final StringRedisTemplate redisTemplate;

    private final IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        // 1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("该博客不存在");
        }
        // 2. 查询blog关联的用户
        queryBlogUser(blog);
        // 3. 查询是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3. 未点赞
            // 3.1 数据库点赞数+1
            boolean isSuccess = this.lambdaUpdate()
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
            // 3.2 保存用户到redis zSet集合 zAdd key value score
            if (isSuccess) {
                redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4. 已点赞
            // 4.1 数据库点赞数-1
            boolean isSuccess = this.lambdaUpdate()
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id)
                    .update();
            // 4.2 从redis set集合中删除用户
            if (isSuccess) {
                redisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top 5点赞用户 zRange key start
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = redisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析出其中的用户信息
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .toList();
        String idStr = StrUtil.join(",", ids);
        // 3. 根据用户id查询用户信息
        List<UserDTO> userDTOList = userService.lambdaQuery()
                .in(User::getId, ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        // 4. 返回
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取当前用户
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());
        // 2. 保存blog
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("保存失败");
        }
        // 3. 查询笔记作者的所有粉丝
        List<Follow> followList = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, userDTO.getId())
                .list();
        // 4. 推送笔记到粉丝的feed
        for (Follow follow : followList) {
            // 4.1 获取粉丝id
            Long userId = follow.getUserId();
            // 4.2 推送
            String key = FEED_KEY + userId;
            redisTemplate.opsForZSet().add(key,
                    blog.getId().toString(),
                    System.currentTimeMillis());
        }
        // 5. 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询收件箱 zRevRangeByScore key max min limit offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3. 非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 4. 解析数据： blogId 、score（时间戳）、offset
        List<Long> blogIds = new ArrayList<>();
        long minTime = 0;
        int offset1 = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 4.1 获取id
            blogIds.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            // 4.2 获取分数
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time == minTime) {
                offset1++;
            } else {
                minTime = time;
                offset1 = 1;
            }
        }
        // 5. 根据blogId查询blog
        List<Blog> blogList = this.lambdaQuery()
                .in(Blog::getId, blogIds)
                .last("ORDER BY FIELD(id, " + StrUtil.join(",", blogIds) + ")")
                .list();

        for (Blog blog : blogList) {
            // 5.1 查询用户信息
            queryBlogUser(blog);
            // 5.2 查询是否点赞
            isBlogLiked(blog);
        }

        // 6. 封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogList);
        r.setMinTime(minTime);
        r.setOffset(offset1);
        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            // 用户未登录，不查询用户信息
            return;
        }
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
