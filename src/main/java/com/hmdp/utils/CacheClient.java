package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
@AllArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REDIS_POOL = Executors.newFixedThreadPool(10);

    // 普通缓存
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 逻辑过期缓存
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透
    // 商铺缓存-缓存穿透-空值返回
    public <R,ID> R queryWithPassThrough(
            String Prefix,
            ID id,
            Class<R> type,
            Function<ID,R> dbRollback,
            Long time,
            TimeUnit unit
    ) {
        // 0. redis key
        String key =  Prefix + id;

        // 1.从redis中获取商品信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson))
            return JSONUtil.toBean(shopJson, type); // 3.存在直接返回

        // 判断是否为空值
        if("".equals(shopJson)) return null; // 如果是空值则返回一个错误信息

        // 4.不存在，到数据库中查询
        R r = dbRollback.apply(id);

        // 5.查询数据库不存在
        if(r == null) {
            this.set(key, "", time, unit);
            return null;
        }

        // 6.存在先缓存到redis
        this.set(key, r, time, unit);

        // 7. 返回
        return r;
    }

    // 缓存击穿-逻辑过期
    public <R,ID> R queryWithLogicExpire(String Prefix, ID id, Class<R> type,
                                         Function<ID,R> dbCallback, Long time, TimeUnit unit) {
        // 0. redis key
        String key = Prefix + id;

        // 1.从redis中获取商品信息
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isBlank(json)) return null; // 3.存在直接返回

        // 4.命中,需要把json反序列为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R bean = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期,直接返回结果
            log.info("cacheClient-缓存过期判断:未过期");
            return bean;
        }

        // 5.2 已经过期,进行复杂的缓存重建逻辑处理
        // 6.缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLook(lockKey);
        // 6.2 判断是否获取成功锁
        if(isLock){
            try {
                log.info("成功获取锁，key-id:{}", LOCK_SHOP_KEY + id);
                // 双重检查...
                CACHE_REDIS_POOL.submit(() -> {
                    try {
                        R apply = dbCallback.apply(id);
                        this.setWithLogicExpire(key, apply, time, unit);
                    } catch (Exception e) {
                        log.error("缓存重建失败", e);
                    } finally {
                        // ✅ 正确：在异步任务完成后释放锁
                        unlock(lockKey);
                        log.info("成功释放锁，key-id:{}", LOCK_SHOP_KEY + id);
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

    // 互斥锁
    private boolean tryLook(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
