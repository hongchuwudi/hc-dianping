package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Component
public class MVCConfig implements WebMvcConfigurer {
    @Resource  // 或者使用 @Autowired
    private LoginInterceptor loginInterceptor;  // 让Spring注入
    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;

//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        // 登录拦截器
//        registry.addInterceptor(loginInterceptor)
//                .addPathPatterns("/**")
//                .excludePathPatterns(
//                        "/user/code",
//                        "/user/login",
//                        "/blog/hot",
//                        "/shop/**",
//                        "/shop-type/**",
//                        "/upload/**",
//                        "/voucher/**"
//                ).order(2);
//        // 刷新token拦截器
//        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(1);
//    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }
}
