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

import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;

/**
 * A kind of {@code BucketLeapArray} that only reserves for future buckets.
 * 一种仅预留未来数据桶的{@code BucketLeapArray}实现。
 * 此类专为资源占用统计设计，不保留历史数据，仅关注分配和管理未来数据桶。
 * @author jialiang.linjl
 * @since 1.5.0
 */
public class FutureBucketLeapArray extends LeapArray<MetricBucket> {
    /**
     * 构造一个FutureBucketLeapArray实例，指定样本数量和间隔时间。
     *
     * @param sampleCount 每个周期中的样本数
     * @param intervalInMs 每个桶之间的毫秒间隔
     */
    public FutureBucketLeapArray(int sampleCount, int intervalInMs) {
        // This class is the original "BorrowBucketArray".
        // 这个类原本被称为"BorrowBucketArray"
        super(sampleCount, intervalInMs);
    }
    /**
     * 创建一个空的MetricBucket。
     * 此方法被重写以创建一个新的空MetricBucket，而不设置开始时间。
     *
     * @param time 当前的时间戳
     * @return 新创建的空MetricBucket
     */
    @Override
    public MetricBucket newEmptyBucket(long time) {
        return new MetricBucket();
    }

    /**
     * 将指定窗口重置到指定的开始时间，并重置桶的数据。
     *
     * @param w 需要重置的窗口
     * @param startTime 新的窗口开始时间
     * @return 重置后的窗口
     */
    @Override
    protected WindowWrap<MetricBucket> resetWindowTo(WindowWrap<MetricBucket> w, long startTime) {
        // Update the start time and reset value.
        // 更新开始时间和重置值。
        w.resetTo(startTime);
        w.value().reset();
        return w;
    }
    /**
     * 判断指定窗口是否过时。
     * 此方法覆盖父类方法，定义窗口过时仅当当前时间大于或等于窗口的开始时间。
     *
     * @param time 当前时间戳
     * @param windowWrap 需要检查的窗口
     * @return 如果窗口过时则返回true，否则返回false
     */
    @Override
    public boolean isWindowDeprecated(long time, WindowWrap<MetricBucket> windowWrap) {
        // Tricky: will only calculate for future.
        // 只计算未来的情况。
        return time >= windowWrap.windowStart();
    }
}
