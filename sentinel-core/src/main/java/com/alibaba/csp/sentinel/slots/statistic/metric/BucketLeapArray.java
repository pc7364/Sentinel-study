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
package com.alibaba.csp.sentinel.slots.statistic.metric;

import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;

/**
 * The fundamental data structure for metric statistics in a time span.
 * 用于时间跨度内度量统计的基础数据结构。
 * @author jialiang.linjl
 * @author Eric Zhao
 * @see LeapArray
 */
public class BucketLeapArray extends LeapArray<MetricBucket> {
    /**
     * 使用指定的样本数量和时间间隔（毫秒）构造BucketLeapArray实例。
     *
     * @param sampleCount 周期内的样本数量，决定了数据存储的容量。
     * @param intervalInMs 时间间隔，以毫秒为单位，决定了每个样本的时间范围。
     */
    public BucketLeapArray(int sampleCount, int intervalInMs) {
        super(sampleCount, intervalInMs);
    }
    /**
     * 创建并返回一个空的MetricBucket实例。
     *
     * @param time 用于初始化Bucket开始时间的毫秒时间戳。
     * @return 一个新的、空的MetricBucket实例。
     */
    @Override
    public MetricBucket newEmptyBucket(long time) {
        return new MetricBucket();
    }
    /**
     * 将指定窗口重置到给定的起始时间，并重置其中的MetricBucket。
     *
     * @param w 包含MetricBucket的WindowWrap对象。
     * @param startTime 用于重置窗口的新的起始时间（毫秒）。
     * @return 更新后的WindowWrap对象。
     */
    @Override
    protected WindowWrap<MetricBucket> resetWindowTo(WindowWrap<MetricBucket> w, long startTime) {
        // Update the start time and reset value.
        // 更新开始时间并重置值。
        w.resetTo(startTime);
        w.value().reset();
        return w;
    }
}
