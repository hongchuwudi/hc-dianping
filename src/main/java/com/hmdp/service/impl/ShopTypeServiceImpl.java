package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate srt;
    @Override
    public Result queryTypeList() {
        // 0.redis key
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY + "type-list";

        // 1.查询redis查看是否有缓存
        List<String> typeListJson = srt.opsForList().range(key, 0, -1);

        // 2.判断是否存在
        if (typeListJson != null && !typeListJson.isEmpty()) {
            // 3.如果存在则直接返回,否则继续下一步
            // 3.1 将JSON字符串转换为ShopType对象列表
            List<ShopType> typeList = typeListJson.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            log.info("查询类型缓存成功");
            // 3.2 返回
           return Result.ok(typeListJson);
        }

        // 4.不存在则查询数据库
        List<ShopType> typeLists = query().orderByAsc("sort").list();

        // 5.不存在则返回错误
        if (typeLists == null) return Result.fail("查询类型列表失败");

        // 6. 将数据存入缓存中
        List<String> jsonList = typeLists.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        srt.opsForList().rightPushAll(key, jsonList);

        // 返回结果
        return Result.ok(typeLists);
    }
}
