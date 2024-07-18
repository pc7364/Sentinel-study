/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.statistic.metric.occupy;

import java.util.List;

import com.alibaba.csp.sentinel.slots.statistic.MetricEvent;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;

/**
 * @author jialiang.linjl
 * @since 1.5.0
 */
public class OccupiableBucketLeapArray extends LeapArray<MetricBucket> {
    /**
     * 用于预分配并准备下一周期的桶的“未来桶跳跃数组”。
     */
    private final FutureBucketLeapArray borrowArray;
    /**
     * 使用指定的样本数量和以毫秒为单位的时间间隔构造一个OccupiableBucketLeapArray实例。
     *
     * @param sampleCount 一个周期内的桶数。
     * @param intervalInMs 两个相邻桶之间的时间间隔（以毫秒为单位）。
     */
    public OccupiableBucketLeapArray(int sampleCount, int intervalInMs) {
        // This class is the original "CombinedBucketArray".
        // 此类为原始的"CombinedBucketArray"
        super(sampleCount, intervalInMs);
        this.borrowArray = new FutureBucketLeapArray(sampleCount, intervalInMs);
    }

    /**
     * 创建一个新的空MetricBucket。
     * 如果在当前时间borrowArray中有可重用的桶，将对其进行重置后复用。
     *
     * @param time 当前时间戳。
     * @return 新创建或重用的MetricBucket。
     */
    @Override
    public MetricBucket newEmptyBucket(long time) {
        MetricBucket newBucket = new MetricBucket();

        MetricBucket borrowBucket = borrowArray.getWindowValue(time);
        if (borrowBucket != null) {
            newBucket.reset(borrowBucket);
        }

        return newBucket;
    }
    /**
     * 将窗口重置到指定时间，并根据可用的借用桶更新桶数据。
     *
     * @param w 要重置的窗口包装器。
     * @param time 目标时间戳。
     * @return 重置后的窗口包装器。
     */
    @Override
    protected WindowWrap<MetricBucket> resetWindowTo(WindowWrap<MetricBucket> w, long time) {
        // Update the start time and reset value.
        // 更新开始时间和重置值。
        w.resetTo(time);
        MetricBucket borrowBucket = borrowArray.getWindowValue(time);
        if (borrowBucket != null) {
            w.value().reset();
            w.value().addPass((int)borrowBucket.pass());
        } else {
            w.value().reset();
        }

        return w;
    }
    /**
     * 计算当前等待处理的总条目数。
     * 通过累加所有借用桶中的通过数来获取当前等待总数。
     *
     * @return 等待条目的总数。
     */
    @Override
    public long currentWaiting() {
        borrowArray.currentWindow();
        long currentWaiting = 0;
        List<MetricBucket> list = borrowArray.values();

        for (MetricBucket window : list) {
            currentWaiting += window.pass();
        }
        return currentWaiting;
    }
    /**
     * 在指定时间添加等待条目数。
     * 更新borrowArray中相应的桶以记录条目数。
     *
     * @param time 当前时间戳。
     * @param acquireCount 要添加的条目数。
     */
    @Override
    public void addWaiting(long time, int acquireCount) {
        WindowWrap<MetricBucket> window = borrowArray.currentWindow(time);
        window.value().add(MetricEvent.PASS, acquireCount);
    }
    /**
     * 调试方法，输出数组及借用数组中所有桶的当前状态。
     *
     * @param time 当前时间戳。
     */
    @Override
    public void debug(long time) {
        StringBuilder sb = new StringBuilder();
        List<WindowWrap<MetricBucket>> lists = listAll();
        sb.append("a_Thread_").append(Thread.currentThread().getId()).append(" time=").append(time).append("; ");
        for (WindowWrap<MetricBucket> window : lists) {
            sb.append(window.windowStart()).append(":").append(window.value().toString()).append(";");
        }
        sb.append("\n");

        lists = borrowArray.listAll();
        sb.append("b_Thread_").append(Thread.currentThread().getId()).append(" time=").append(time).append("; ");
        for (WindowWrap<MetricBucket> window : lists) {
            sb.append(window.windowStart()).append(":").append(window.value().toString()).append(";");
        }
        System.out.println(sb.toString());
    }
}
