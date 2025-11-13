package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl ShopService;

    @Resource RedisIdWorker redisIdWorker;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testSaveShop() {
        ShopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        // 创建线程池
        CountDownLatch time = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            time.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有任务完成
        time.await();

        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));

        // 关闭线程池
        es.shutdown();
    }
}
