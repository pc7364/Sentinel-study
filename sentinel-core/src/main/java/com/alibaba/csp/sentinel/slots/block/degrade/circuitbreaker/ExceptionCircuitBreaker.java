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

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;

import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT;
import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO;

/**
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ExceptionCircuitBreaker extends AbstractCircuitBreaker {

    private final int strategy;
    private final int minRequestAmount;
    private final double threshold;

    private final LeapArray<SimpleErrorCounter> stat;

    public ExceptionCircuitBreaker(DegradeRule rule) {
        this(rule, new SimpleErrorCounterLeapArray(1, rule.getStatIntervalMs()));
    }

    ExceptionCircuitBreaker(DegradeRule rule, LeapArray<SimpleErrorCounter> stat) {
        super(rule);
        this.strategy = rule.getGrade();
        boolean modeOk = strategy == DEGRADE_GRADE_EXCEPTION_RATIO || strategy == DEGRADE_GRADE_EXCEPTION_COUNT;
        AssertUtil.isTrue(modeOk, "rule strategy should be error-ratio or error-count");
        AssertUtil.notNull(stat, "stat cannot be null");
        this.minRequestAmount = rule.getMinRequestAmount();
        this.threshold = rule.getCount();
        this.stat = stat;
    }

    @Override
    protected void resetStat() {
        // Reset current bucket (bucket count = 1).
        stat.currentWindow().value().reset();
    }

    /**
     * 处理请求完成后的逻辑。
     * 此方法主要负责统计当前请求的错误信息，并更新相关统计指标。
     * 如果请求关联的条目存在错误，会增加错误计数；无论是否存在错误，都会增加总请求计数。
     * 当错误阈值超过时，会触发相应的状态处理。
     *
     * @param context 上下文对象，包含本次请求的相关信息。
     */
    @Override
    public void onRequestComplete(Context context) {
        // 获取当前请求的条目信息。
        Entry entry = context.getCurEntry();
        // 如果当前条目为空，则直接返回，不进行后续处理。
        if (entry == null) {
            return;
        }
        // 获取条目中的错误信息。
        Throwable error = entry.getError();
        // 获取当前统计窗口的错误计数器。
        SimpleErrorCounter counter = stat.currentWindow().value();
        // 如果存在错误，增加错误计数。
        if (error != null) {
            counter.getErrorCount().add(1);
        }
        // 无论是否存在错误，都增加总请求计数。
        counter.getTotalCount().add(1);

        // 处理错误阈值超过时的状态变化。
        handleStateChangeWhenThresholdExceeded(error);
    }

    /**
     * 当阈值被超过时处理状态变化。
     * 此方法主要用于根据错误率或错误数量决定是否将当前状态切换为OPEN。
     * 如果当前状态已经是OPEN，不做任何操作。
     * 如果当前状态是HALF_OPEN，它会根据当前请求是否失败来决定是切换回CLOSED还是回到OPEN状态。
     * 对于其他状态，它计算错误率或错误数量，并根据阈值决定是否切换到OPEN状态。
     *
     * @param error 在当前请求中抛出的异常，用于在状态为HALF_OPEN时确定状态转换。
     */
    private void handleStateChangeWhenThresholdExceeded(Throwable error) {
        // 如果当前状态是OPEN，不处理状态变化
        if (currentState.get() == State.OPEN) {
            return;
        }
        // 如果当前状态是HALF_OPEN
        if (currentState.get() == State.HALF_OPEN) {
            // In detecting request
            // 如果没有错误，切换到CLOSED状态
            // 在检测请求
            if (error == null) {
                //半开——>CLOSE
                fromHalfOpenToClose();
            } else {
                // 如果有错误，重新切换到OPEN状态 半开->OPEN
                fromHalfOpenToOpen(1.0d);
            }
            return;
        }
        // 获取所有错误计数器以计算总错误数和请求数量
        List<SimpleErrorCounter> counters = stat.values();
        long errCount = 0;
        long totalCount = 0;
        for (SimpleErrorCounter counter : counters) {
            errCount += counter.errorCount.sum();
            totalCount += counter.totalCount.sum();
        }
        // 如果总请求数量未达到最小要求，不处理状态变化
        if (totalCount < minRequestAmount) {
            return;
        }
        // 计算当前的错误率或错误数量
        double curCount = errCount;
        // 如果降级策略是ERROR_RATIO，计算错误率
        if (strategy == DEGRADE_GRADE_EXCEPTION_RATIO) {
            // Use errorRatio
            curCount = errCount * 1.0d / totalCount;
        }
        // 如果当前的错误率或错误数量超过阈值，切换到OPEN状态
        if (curCount > threshold) {
            transformToOpen(curCount);
        }
    }

    static class SimpleErrorCounter {
        private LongAdder errorCount;
        private LongAdder totalCount;

        public SimpleErrorCounter() {
            this.errorCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getErrorCount() {
            return errorCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        public SimpleErrorCounter reset() {
            errorCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SimpleErrorCounter{" +
                "errorCount=" + errorCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    static class SimpleErrorCounterLeapArray extends LeapArray<SimpleErrorCounter> {

        public SimpleErrorCounterLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SimpleErrorCounter newEmptyBucket(long timeMillis) {
            return new SimpleErrorCounter();
        }

        @Override
        protected WindowWrap<SimpleErrorCounter> resetWindowTo(WindowWrap<SimpleErrorCounter> w, long startTime) {
            // Update the start time and reset value.
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }
}
