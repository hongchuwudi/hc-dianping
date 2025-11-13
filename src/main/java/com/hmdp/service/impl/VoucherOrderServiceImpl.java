package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //  1. 查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //  2.判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(voucher.getBeginTime()) ){
            // 秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //  3.判断秒杀是否已经结束
        if(now.isAfter(voucher.getEndTime())){
            // 秒杀已经结束
            return Result.fail("秒杀已经结束");
        }

        //  4.判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        // 5.创建一人一单的枷锁方案
        // 查询用户
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            // 获取代理对象(事物相关)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //  5.一人一单
        Long userId = UserHolder.getUser().getId();
        //  5.1 查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        //  5,2 判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.info("用户:{}已经购买过一次秒杀劵:{}了", userId, voucherId);
            return Result.fail("用户已经购买过一次");
        }

        //  6.扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
//                .eq("stock", voucher.getStock()) // CAS 保证库存一致性(仿版本号法)
                .gt("stock",0)              // CAS 保证库一致性(更好的写法)
                .update();

        //  7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1 创建订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2 用户Id
        voucherOrder.setUserId(userId);
        // 7.3 优惠券Id
        voucherOrder.setVoucherId(voucherId);
        //  8.返回订单结果
        return Result.ok(orderId);
    }
}
