package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.  查询商品缓存
        List<String> cacheShopTypeList = redisTemplate.opsForList().range(SHOP_TYPE_LIST_KEY, 0, -1);
        if (CollUtil.isNotEmpty(cacheShopTypeList)) {
            List<ShopType> shopTypeList = cacheShopTypeList.stream()
                    .map(s -> JSONUtil.toBean(s, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }
        // 2. 缓存中没有，则查询数据库
        List<ShopType> shopTypeList = this.lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list();
        // 2.1 不存在，则返回失败信息
        if (CollUtil.isEmpty(shopTypeList)) {
            return Result.fail("商品类型不存在");
        }
        // 3. 数据库查询成功，则将数据缓存到redis
        List<String> shopTypeStrList = shopTypeList.stream()
                .map(JSONUtil::toJsonStr)
                .toList();
        redisTemplate.opsForList().rightPushAll(SHOP_TYPE_LIST_KEY, shopTypeStrList);
        // 4. 返回查询结果
        return Result.ok(shopTypeList);
    }
}
