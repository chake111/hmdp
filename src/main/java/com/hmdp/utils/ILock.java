package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

/**
 * @author chake
 * @since 2025/8/7
 */

public interface ILock {

    boolean tryLock(long timeoutSeconds);

    void unlock();
}
