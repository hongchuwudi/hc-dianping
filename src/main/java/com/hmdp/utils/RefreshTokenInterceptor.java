package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取token头
        String token = request.getHeader("authorization");
        log.info("RefreshToken拦截层-用户登录的token为：{}",token);
        // 2.判断token是否存在
        if(StrUtil.isBlank(token)) return true; // 不存在返回给下一层拦截器:token拦截器

        // 3.基于token获取用户
        Map<Object, Object> entries = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);

        // 4. 判断用户是否存在
        if(entries.isEmpty()) return true; // 如果不存在也放行给下一层拦截器

        // 5.存在，将查到的user转化成为DTO对象
        // Long 需要转化成 string
        UserDTO userdto = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        log.info("RefreshToken拦截层-用户登录的token为：{}",userdto);

        // 6.存储用户到threadLocal
        UserHolder.saveUser(userdto);

        // 7.刷新redis的过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 1.移除用户
        UserHolder.removeUser();
    }
}
