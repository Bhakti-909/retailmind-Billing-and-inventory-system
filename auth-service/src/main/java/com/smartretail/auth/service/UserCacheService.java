package com.smartretail.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartretail.auth.entity.User;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserCacheService {

    private static final long CACHE_TTL_HOURS = 6;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void cacheUser(User user) {

        try {

            String key =
                    "user:" +
                    user.getUsername();

            Map<String, Object> data =
                    new HashMap<>();

            data.put(
                    "id",
                    user.getId()
            );

            data.put(
                    "businessId",
                    user.getBusiness().getId()
            );

            data.put(
                    "username",
                    user.getUsername()
            );

            data.put(
                    "email",
                    user.getEmail()
            );

            data.put(
                    "role",
                    user.getRole()
            );

            data.put(
                    "enabled",
                    user.isEnabled()
            );

            String json =
                    objectMapper.writeValueAsString(
                            data
                    );

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    CACHE_TTL_HOURS,
                    TimeUnit.HOURS
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to cache user.",
                    e
            );
        }
    }

    public String getCachedUser(
            String username
    ) {

        String key =
                "user:" + username;

        return redisTemplate
                .opsForValue()
                .get(key);
    }

    public void removeUser(
            String username
    ) {

        redisTemplate.delete(
                "user:" + username
        );
    }
}