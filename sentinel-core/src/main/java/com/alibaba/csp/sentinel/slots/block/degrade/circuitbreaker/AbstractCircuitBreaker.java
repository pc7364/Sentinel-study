/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;

/**
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {
    //降级规则
    protected final DegradeRule rule;
    // 恢复超时时间（毫秒）
    protected final int recoveryTimeoutMs;
    //断路器状态变化的观察者注册表
    private final EventObserverRegistry observerRegistry;
    //电路断路器的状态
    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    //下一次重试的时间戳
    protected volatile long nextRetryTimestamp;

    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }

    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

    // 尝试让请求通过断路器
    @Override
    public boolean tryPass(Context context) {
        // Template implementation.
        // 在CLOSED状态，直接允许通过
        if (currentState.get() == State.CLOSED) {
            return true;
        }
        // 在OPEN状态，仅在半开状态下允许探测请求
        if (currentState.get() == State.OPEN) {
            // For half-open state we allow a request for probing.
            // 对于半开放状态，允许探测请求。
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }
        return false;
    }

    /**
     * Reset the statistic data.
     */
    abstract void resetStat();

    // 判断是否到达重试时间
    protected boolean retryTimeoutArrived() {
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }

    protected void updateNextRetryTimestamp() {
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }

    protected boolean fromCloseToOpen(double snapshotValue) {
        State prev = State.CLOSED;
        if (currentState.compareAndSet(prev, State.OPEN)) {
            updateNextRetryTimestamp();

            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    // 从OPEN状态转换到HALF_OPEN状态
    protected boolean fromOpenToHalfOpen(Context context) {
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            // 通知观察者状态已从开启变更为半开启
            notifyObservers(State.OPEN, State.HALF_OPEN, null);

            // 获取当前的入口对象
            Entry entry = context.getCurEntry();
            // 设置当此次尝试通过的请求结束时执行的逻辑
            entry.whenTerminate(new BiConsumer<Context, Entry>() {
                @Override
                public void accept(Context context, Entry entry) {
                    // Note: This works as a temporary workaround for https://github.com/alibaba/Sentinel/issues/1638
                    // Without the hook, the circuit breaker won't recover from half-open state in some circumstances
                    // when the request is actually blocked by upcoming rules (not only degrade rules).
                    if (entry.getBlockError() != null) {
                        // Fallback to OPEN due to detecting request is blocked
                        //由于检测到请求被阻止而退回到OPEN
                        currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                        notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                    }
                }
            });
            return true;
        }
        return false;
    }
    
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    /**
     * 尝试将状态从半开转换为开。
     * 当系统处于半开状态，并且满足某些条件（例如，一个预定义的尝试次数）时，可能需要将状态转换为开。
     * 这个方法用于处理这种状态转换。
     *
     * @param snapshotValue 当前的快照值，这个参数可能用于未来的扩展，例如基于当前值的某些条件来决定是否转换状态。
     * @return 如果状态成功从半开转换为开，则返回true；否则返回false，表示状态转换失败。
     */
    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        // 尝试使用CAS操作将状态从HALF_OPEN改为OPEN。如果成功，继续执行后续操作。
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            // 更新下一次重试的时间戳，表示在经历了一次失败后，系统需要等待一段时间才能再次尝试。
            updateNextRetryTimestamp();
            // 通知观察者状态已从HALF_OPEN变为OPEN，同时传递当前的快照值。
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        // 如果CAS操作失败，则表示状态转换竞争失败，此时返回false。
        return false;
    }

    /**
     * 尝试将状态从半开状态转换为关闭状态。
     * 当状态从HALF_OPEN成功转变为CLOSED时，重置相关状态信息，并通知观察者状态已变更。
     * 这个方法用于处理故障恢复后或达到一定重试次数后，关闭状态的切换逻辑。
     *
     * @return 如果状态成功转变为CLOSED，则返回true；否则返回false，表示状态转换失败。
     */
    protected boolean fromHalfOpenToClose() {
        // 尝试使用CAS操作将当前状态从HALF_OPEN更新为CLOSED
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            // 状态更新成功后，重置相关状态统计信息
            resetStat();
            // 通知所有观察者，状态从HALF_OPEN变化为CLOSED，故障处理结束
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        // 如果状态更新失败，则返回false
        return false;
    }

    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
}
