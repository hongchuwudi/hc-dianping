package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate srt;
    private static final ExecutorService CACHE_REDIS_POOL = Executors.newFixedThreadPool(10);
    @Resource private CacheClient rc;
    /**
     * 商铺缓存-综合多种缓存问题的解决方案
     * @param id 商户id
     * @return 商户信息
     */
    @Override
    public Result queryIByd(Long id) throws InterruptedException {
        Shop shop;
         // 1. 缓存穿透
//        Shop = queryWithPassThrough(id);
//          shop = rc.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
         // 2. 缓存击穿-互斥锁
//         shop = queryWithMutex(id);
         // 3. 缓存击穿-逻辑过期
//         shop = queryWithLogicExpire(id);
        shop = rc.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        // 7. 返回
        return Result.ok(shop);
    }

    // 商铺缓存-缓存穿透-空值返回
    public Shop queryWithPassThrough(Long id) {
        // 0. redis key
        String key = CACHE_SHOP_KEY + id;

        // 1.从redis中获取商品信息
        String shopJson = srt.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson))
            return JSONUtil.toBean(shopJson, Shop.class); // 3.存在直接返回

        // 判断是否为空值
        if("".equals(shopJson)) return null; // 如果是空值则返回一个错误信息

        // 4.不存在，到数据库中查询
        Shop shopping = query().eq("id", id).one();

        // 5.查询数据库不存在
        if(shopping == null) {
            srt.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6.存在先缓存到redis
        srt.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shopping), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7. 返回
        return shopping;
    }

    // 商铺缓存-缓存击穿-互斥锁实现
    public Shop queryWithMutex(Long id) throws InterruptedException {
        // 0. redis key
        String key = CACHE_SHOP_KEY + id;

        // 1.从redis中获取商铺信息
        String shopJson = srt.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson))
            return JSONUtil.toBean(shopJson, Shop.class); // 3.存在直接返回

        // 判断是否为空值
        if("".equals(shopJson)) return null; // 如果是空值则返回一个空值


        // 4.不存在，实现缓存重建
        // 4.1 获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shopping;
        try {
            boolean isLock = tryLook(lockKey);

            // 4.2 获取失败
            if(!isLock){
                // 4.3 失败休眠0.05s
                log.info("等待锁释放-50ms");
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 4.4 获取成功查询数据库
            Thread.sleep(200);
           shopping = query().eq("id", id).one();

            // 5.查询数据库不存在
            if(shopping == null) {
                srt.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6.存在先缓存到redis
            srt.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shopping), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放锁
            unlock(lockKey);
        }

        // 8. 返回
        return shopping;

    }

    // 互斥锁
    private boolean tryLook(String key){
        Boolean flag = srt.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key){
        srt.delete(key);
    }

    // 商铺缓存-缓存击穿-逻辑过期-key永久有效保存方法
    public void saveShop2Redis(Long id,Long expireTime){
        // 1.查询数据库
        Shop shop = getById(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));

        // 3.写入Redis
        srt.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    // 商铺缓存-缓存击穿-逻辑过期
    public Shop queryWithLogicExpire(Long id) {
        // 0. redis key
        String key = CACHE_SHOP_KEY + id;

        // 1.从redis中获取商品信息
        String shopJson = srt.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) return null; // 3.存在直接返回

        // 4.命中,需要把json反序列为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject shop = (JSONObject) redisData.getData();
        Shop bean = JSONUtil.toBean(shop, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1未过期,直接返回结果
            return bean;
        }
        // 5.2已经过期,进行复杂的缓存重建逻辑处理
        // 6.缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLook(lockKey);
        // 6.2 判断是否获取成功锁
        if(isLock){
            try {
                // 双重检查...
                CACHE_REDIS_POOL.submit(() -> {
                    try {
                        log.info("重建缓存-线程-开始 id:{}", id);
                        this.saveShop2Redis(id, 20L);
                        log.info("重建缓存-线程-完成 id:{}", id);
                    } catch (Exception e) {
                        log.error("缓存重建失败", e);
                    } finally {
                        // ✅ 正确：在异步任务完成后释放锁
                        unlock(lockKey);
                        log.info("释放锁，id:{}", id);
                    }
                });
            } catch (Exception e) {
                // ✅ 只有任务提交失败时才在这里释放锁
                unlock(lockKey);
                log.error("提交重建任务失败，释放锁", e);
            }
        }
        return bean;
    }

    /**
     * 商户信息修改-多种缓存策略
     * @param shop 商户信息
     * @return 修改结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        // 检查该id商品是否存在
        Long id = shop.getId();
        if(id == null) return Result.fail("店铺id不能为空");

        // 缓存策略: 旁路缓存 Cache-Aside pattern
        // 1. 写入数据库
        updateById(shop);

        // 2. 删除缓存
        srt.delete(CACHE_SHOP_KEY + id);

        // 3. 返回成功
        return Result.ok();
    }
}
