package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate srt;
    
    @Override
    public Result queryIByd(Long id) {
        // 0. redis key
        String key = CACHE_SHOP_KEY + id;

        // 1.从redis中获取商品信息
        String shopJson = srt.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在直接返回
            JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shopJson);
        }

        // 4.不存在，到数据库中查询
        Shop shopping = query().eq("id", id).one();

        // 5.查询数据库不存在
        if(shopping == null) return Result.fail("商品不存在");

        // 6.存在先缓存到redis
        srt.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shopping));

        // 7. 返回
        return Result.ok(shopping);
    }
}
