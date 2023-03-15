package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override

    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("你小子别乱搞嗷！");
        }
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())
                || LocalDateTime.now().isAfter(voucher.getEndTime())) {
            return Result.fail("不在活动时间");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("抢完了！");
        }


        /*
         在集群模式下会发生线程安全问题！改用分布式锁解决
        // 先获取锁，再提交事务，最后释放锁
        synchronized (uid.toString().intern()) {
            // return this.createVoucherOrder(voucherId); spring事务可能失效，得用代理对象
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
        */
        Long uid = UserHolder.getUser().getId();
        SimpleRedisLock redisLock = new SimpleRedisLock("seckillOrder" + uid, stringRedisTemplate);
        try {
            if (redisLock.tryLock(RedisConstants.DISTRIBUTED_LOCK_EXPIRE_SEC)) {
                // 别忘了加入AspectJ
                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                return proxy.createVoucherOrder(voucherId);
            }
            return Result.fail("You have already got one!");
        } finally {
            redisLock.unlock();
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单判断
        Long uid = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        // 通过用户id和优惠券id查询
        queryWrapper.eq(VoucherOrder::getUserId, uid).eq(VoucherOrder::getVoucherId, voucherId);
        if (count(queryWrapper) != 0) {
            return Result.fail("一人一单！");
        }

        // 扣减库存
        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, voucherId).gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1");
        boolean success = seckillVoucherService.update(wrapper);
        if (!success) {
            return Result.fail("抢完了！");
        }

        // 创建voucherOrder
        VoucherOrder order = new VoucherOrder();
        // 生成全局唯一订单id
        long orderId = redisWorker.nextId("seckill");
        order.setId(orderId);
        // 设置用户id 通过UserHolder拿到用户id

        order.setUserId(uid);
        // 设置优惠券id
        order.setVoucherId(voucherId);
        save(order);
        return Result.ok(orderId);

    }
}
