package com.fastrag.module.application.service.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 停止注册表并发协议（tdd/new-conversation.md S4；contract new-conversation.StopRegistry）。
 */
class StopRegistryTest {

    private StopRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StopRegistry();
    }

    @Test
    void cancelFirstTimeTriggersPartialSaveExactlyOnce() {
        // [幂等] cancel 首次生效恰一次落库
        AtomicInteger saves = new AtomicInteger();
        registry.register("c1", new StopRegistry.CancelHandle(saves::incrementAndGet));

        boolean triggered = registry.cancel("c1");

        assertThat(triggered).isTrue();
        assertThat(saves.get()).isEqualTo(1);
    }

    @Test
    void cancelTwiceIsIdempotent() {
        // [幂等] 二次 cancel 无效果
        AtomicInteger saves = new AtomicInteger();
        registry.register("c1", new StopRegistry.CancelHandle(saves::incrementAndGet));
        registry.cancel("c1");

        boolean second = registry.cancel("c1");

        assertThat(second).isFalse();
        assertThat(saves.get()).isEqualTo(1);
    }

    @Test
    void laterRegistrationReplacesCancelTarget() {
        // [覆盖] 后注册取代取消目标（并发双流互斥不在本契约——债务已登记）
        AtomicInteger firstSaves = new AtomicInteger();
        AtomicInteger secondSaves = new AtomicInteger();
        registry.register("c1", new StopRegistry.CancelHandle(firstSaves::incrementAndGet));
        registry.register("c1", new StopRegistry.CancelHandle(secondSaves::incrementAndGet));

        boolean triggered = registry.cancel("c1");

        assertThat(triggered).isTrue();
        assertThat(secondSaves.get()).isEqualTo(1);
        assertThat(firstSaves.get()).isEqualTo(0);
    }

    @Test
    void cancelAfterUnregisterHasNoEffect() {
        // [清理] unregister 后 cancel 无效
        AtomicInteger saves = new AtomicInteger();
        registry.register("c1", new StopRegistry.CancelHandle(saves::incrementAndGet));
        registry.unregister("c1");

        boolean triggered = registry.cancel("c1");

        assertThat(triggered).isFalse();
        assertThat(saves.get()).isEqualTo(0);
        assertThat(registry.isActive("c1")).isFalse();
    }

    @Test
    void concurrentCancelSavesExactlyOnce() throws InterruptedException {
        // [并发] 双线程同 cancel 恰一次落库（AtomicBoolean 幂等守卫）
        AtomicInteger saves = new AtomicInteger();
        registry.register("c1", new StopRegistry.CancelHandle(saves::incrementAndGet));
        CountDownLatch latch = new CountDownLatch(2);
        Runnable r = () -> {
            registry.cancel("c1");
            latch.countDown();
        };
        new Thread(r).start();
        new Thread(r).start();
        latch.await();

        assertThat(saves.get()).isEqualTo(1);
    }
}
