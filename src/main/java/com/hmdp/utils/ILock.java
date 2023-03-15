package com.hmdp.utils;

/**
 * @author Acer
 * @create 2023/3/15 14:02
 */
public interface ILock {
    /**
     * @param timeoutSec 超时自动释放时间，以秒为单位
     * @return 成功获取锁返回true，否则返回false
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
