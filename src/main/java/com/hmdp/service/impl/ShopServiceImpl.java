package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        // 7. 返回成功信息
        return Result.ok(shop);
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 避免自动拆箱
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回缓存数据
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            // 返回错误
            return null;
        }
        // 4. 不存在，查询数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            // 存入null值，防止缓存穿透
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 5. 不存在，返回失败信息
            return null;
        }
        // 6. 存在，写入redis
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. 返回成功信息
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = null;
        Shop shop;
        try {
            // 1. 从redis查询商铺缓存
            String shopJson = redisTemplate.opsForValue().get(key);
            // 2. 判断是否存在
            if (StrUtil.isNotBlank(shopJson)) {
                // 3. 存在，直接返回缓存数据
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            if (shopJson != null) {
                // 返回错误
                return null;
            }
            // 4. 实现缓存重建
            // 4.1 获取互斥锁
            lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否获取到锁
            if (!isLock) {
                // 4.3 失败，休眠后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // double check
            String shopJson1 = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson1)) {
                return JSONUtil.toBean(shopJson1, Shop.class);
            }
            // 4.4 成功，查询数据库
            shop = this.getById(id);
            if (shop == null) {
                // 存入null值，防止缓存穿透
                redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 5. 不存在，返回失败信息
                return null;
            }
            // 6. 存在，写入redis
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放互斥锁
            if (lockKey != null) {
                unlock(lockKey);
            }
        }
        // 8. 返回成功信息
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("商铺id不能为空");
        }
        // 1. 更新数据库
        this.updateById(shop);
        // 2. 删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        redisTemplate.delete(key);
        return Result.ok();
    }
}
