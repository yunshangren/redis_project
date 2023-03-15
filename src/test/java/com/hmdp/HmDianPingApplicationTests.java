package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisWorker worker;

    private static ExecutorService threadPool = Executors.newFixedThreadPool(500);

    @Test
    public void saveShop2RedisTest() {
        List<Shop> shops = shopService.list();
        for (Shop shop : shops) {
            shopService.saveShop2Redis(shop.getId(), 10);
        }
    }

    @Test
    public void testIDWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            threadPool.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    long id = worker.nextId("order");
                    System.out.println(id);
                }
                latch.countDown();
            });
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("use time :" + (end - begin) + "ms");
    }
}
