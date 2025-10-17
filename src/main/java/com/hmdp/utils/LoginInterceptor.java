package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import cn.hutool.core.bean.BeanUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取token头
        String token = request.getHeader("authorization");
        log.info("用户登录的token为：{}",token);

        // 2.判断token是否存在
        if(StrUtil.isBlank(token)){
            // 不存在报错返回
            log.info("用户未登录,token不存在");
            response.setStatus(401);
            return false;
        }

        // 3.基于token获取用户
        Map<Object, Object> entries = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        if(entries.isEmpty()){
            // 4.不存在报错返回
            log.info("用户未登录,用户不存在");
            response.setStatus(401);
            return false;
        }

        // 5.存在，将查到的user转化成为DTO对象
        // Long 需要转化成 string
        UserDTO userdto = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

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
