package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.service.impl.ShopTypeServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Acer
 * @create 2023/3/7 17:38
 */
@SpringBootTest
public class MyTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeServiceImpl shopTypeService;
    @Test
    public void test(){
        stringRedisTemplate.opsForValue().set("name","zhangsan");
    }


}
