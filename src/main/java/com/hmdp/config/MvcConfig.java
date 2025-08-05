package com.hmdp.config;

import com.hmdp.interceptors.LoginInterceptor;
import com.hmdp.interceptors.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author chake
 * @since 2025/8/5
 */
@Configuration
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "blog/hot",
                        "shop/**",
                        "shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
        // 刷新token拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).order(0);
    }
}
