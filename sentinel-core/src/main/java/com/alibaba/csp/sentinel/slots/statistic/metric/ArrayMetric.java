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

import java.util.ArrayList;
import java.util.List;

import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.MetricEvent;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.metric.occupy.OccupiableBucketLeapArray;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * The basic metric class in Sentinel using a {@link BucketLeapArray} internal.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */

 //ArrayMetric 类实现了 Metric 接口，用于统计指标数据。
 //它内部使用 BucketLeapArray 来管理不同时窗的指标桶，提供了一系列方法来获取和更新统计指标。
public class ArrayMetric implements Metric {

    /**
     * 存储指标数据的 LeapArray，用于管理不同时窗的指标桶。
     */
    private final LeapArray<MetricBucket> data;

    /**
     * 构造函数，创建一个不支持占用的 ArrayMetric 实例。
     *
     * @param sampleCount 每个桶中样本的数目。
     * @param intervalInMs 每个桶的时间间隔，以毫秒为单位。
     */
    public ArrayMetric(int sampleCount, int intervalInMs) {
        this.data = new OccupiableBucketLeapArray(sampleCount, intervalInMs);
    }


    /**
     * 构造函数，创建一个可配置是否支持占用的 ArrayMetric 实例。
     *
     * @param sampleCount 每个桶中样本的数目。
     * @param intervalInMs 每个桶的时间间隔，以毫秒为单位。
     * @param enableOccupy 是否启用占用功能。
     */
    public ArrayMetric(int sampleCount, int intervalInMs, boolean enableOccupy) {
        if (enableOccupy) {
            this.data = new OccupiableBucketLeapArray(sampleCount, intervalInMs);
        } else {
            this.data = new BucketLeapArray(sampleCount, intervalInMs);
        }
    }

    /**
     * For unit test.
     */
    public ArrayMetric(LeapArray<MetricBucket> array) {
        this.data = array;
    }

    /**
     * 获取所有窗口中成功通过的请求数的总和。
     *
     * @return 成功通过的请求数的总和。
     */
    @Override
    public long success() {
        data.currentWindow();
        long success = 0;

        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            success += window.success();
        }
        return success;
    }

    /**
     * 获取所有窗口中成功通过请求数的最大值。
     *
     * @return 成功通过请求数的最大值。
     */
    @Override
    public long maxSuccess() {
        data.currentWindow();
        long success = 0;

        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            if (window.success() > success) {
                success = window.success();
            }
        }
        return Math.max(success, 1);
    }

    /**
     * 获取所有窗口中异常请求数的总和。
     *
     * @return 异常请求数的总和。
     */
    @Override
    public long exception() {
        data.currentWindow();
        long exception = 0;
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            exception += window.exception();
        }
        return exception;
    }

    /**
     * 获取所有窗口中被阻塞的请求数的总和。
     *
     * @return 被阻塞的请求数的总和。
     */
    @Override
    public long block() {
        data.currentWindow();
        long block = 0;
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            block += window.block();
        }
        return block;
    }

    /**
     * 获取所有窗口中通过的请求数的总和。
     *
     * @return 通过的请求数的总和。
     */
    @Override
    public long pass() {
        data.currentWindow();
        long pass = 0;
        List<MetricBucket> list = data.values();

        for (MetricBucket window : list) {
            pass += window.pass();
        }
        return pass;
    }

    /**
     * 获取所有窗口中被占用通过的请求数的总和。
     *
     * @return 被占用通过的请求数的总和。
     */
    @Override
    public long occupiedPass() {
        data.currentWindow();
        long pass = 0;
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            pass += window.occupiedPass();
        }
        return pass;
    }

    /**
     * 获取所有窗口中请求响应时间（RT）的总和。
     *
     * @return 请求响应时间（RT）的总和。
     */
    @Override
    public long rt() {
        data.currentWindow();
        long rt = 0;
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            rt += window.rt();
        }
        return rt;
    }

    /**
     * 获取所有窗口中最小的请求响应时间（RT）。
     *
     * @return 最小的请求响应时间（RT）。
     */
    @Override
    public long minRt() {
        data.currentWindow();
        long rt = SentinelConfig.statisticMaxRt();
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            if (window.minRt() < rt) {
                rt = window.minRt();
            }
        }

        return Math.max(1, rt);
    }

    /**
     * 获取所有窗口的详细指标数据列表。
     *
     * @return 所有窗口的详细指标数据列表。
     */
    @Override
    public List<MetricNode> details() {
        List<MetricNode> details = new ArrayList<>();
        data.currentWindow();
        List<WindowWrap<MetricBucket>> list = data.list();
        for (WindowWrap<MetricBucket> window : list) {
            if (window == null) {
                continue;
            }

            details.add(fromBucket(window));
        }

        return details;
    }

    /**
     * 根据条件获取窗口的详细指标数据列表。
     *
     * @param timePredicate 时间条件谓词，用于过滤窗口。
     * @return 符合条件的窗口的详细指标数据列表。
     */
    @Override
    public List<MetricNode> detailsOnCondition(Predicate<Long> timePredicate) {
        List<MetricNode> details = new ArrayList<>();
        data.currentWindow();
        List<WindowWrap<MetricBucket>> list = data.list();
        for (WindowWrap<MetricBucket> window : list) {
            if (window == null) {
                continue;
            }
            if (timePredicate != null && !timePredicate.test(window.windowStart())) {
                continue;
            }

            details.add(fromBucket(window));
        }

        return details;
    }

    /**
     * 从桶中创建一个 MetricNode 对象。
     *
     * @param wrap 指定窗口的包装对象。
     * @return 从桶中提取的 MetricNode 对象。
     */
    private MetricNode fromBucket(WindowWrap<MetricBucket> wrap) {
        MetricNode node = new MetricNode();
        node.setBlockQps(wrap.value().block());
        node.setExceptionQps(wrap.value().exception());
        node.setPassQps(wrap.value().pass());
        long successQps = wrap.value().success();
        node.setSuccessQps(successQps);
        if (successQps != 0) {
            node.setRt(wrap.value().rt() / successQps);
        } else {
            node.setRt(wrap.value().rt());
        }
        node.setTimestamp(wrap.windowStart());
        node.setOccupiedPassQps(wrap.value().occupiedPass());
        return node;
    }

    /**
     * 获取所有窗口的指标桶数组。
     *
     * @return 所有窗口的指标桶数组。
     */
    @Override
    public MetricBucket[] windows() {
        data.currentWindow();
        return data.values().toArray(new MetricBucket[0]);
    }

    /**
     * 在当前窗口中增加异常请求数。
     *
     * @param count 要增加的异常请求数。
     */
    @Override
    public void addException(int count) {
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        wrap.value().addException(count);
    }

    /**
     * 在当前窗口中增加被阻塞的请求数。
     *
     * @param count 要增加的被阻塞的请求数。
     */
    @Override
    public void addBlock(int count) {
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        wrap.value().addBlock(count);
    }

    /**
     * 增加等待时间和服务端口。
     *
     * @param time 等待时间。
     * @param acquireCount 获取计数。
     */
    @Override
    public void addWaiting(long time, int acquireCount) {
        data.addWaiting(time, acquireCount);
    }

    /**
     * 在当前窗口中增加被占用通过的请求数。
     *
     * @param acquireCount 要增加的被占用通过的请求数。
     */
    @Override
    public void addOccupiedPass(int acquireCount) {
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        wrap.value().addOccupiedPass(acquireCount);
    }

    /**
     * 在当前窗口中增加成功通过的请求数。
     *
     * @param count 要增加的成功通过的请求数。
     */
    @Override
    public void addSuccess(int count) {
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        wrap.value().addSuccess(count);
    }

    /**
     * 增加通过数量。
     * 此方法用于在当前窗口中增加指定数量的通过计数。它首先获取当前的数据窗口，然后通过窗口的值对象来增加通过计数。
     * 这种设计允许对数据窗口的操作保持封装，同时提供了一种灵活的方式来更新窗口内的统计信息。
     *
     * @param count 要增加的通过数量。此参数用于指定在当前窗口中应增加的通过计数。
     */
    @Override
    public void addPass(int count) {
        // 获取当前窗口的包装对象
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        // 在当前窗口的MetricBucket中增加通过数量
        wrap.value().addPass(count);
    }

    /**
     * 在当前窗口中增加请求响应时间（RT）。
     *
     * @param rt 响应时间。
     */
    @Override
    public void addRT(long rt) {
        WindowWrap<MetricBucket> wrap = data.currentWindow();
        wrap.value().addRT(rt);
    }

    /**
     * 进入调试模式，打印当前时间点的统计信息。
     *
     * @param currentTimeMillis 当前时间戳。
     */
    @Override
    public void debug() {
        data.debug(System.currentTimeMillis());
    }

    /**
     * 获取前一个窗口的阻塞请求数。
     *
     * @return 前一个窗口的阻塞请求数。
     */
    @Override
    public long previousWindowBlock() {
        data.currentWindow();
        WindowWrap<MetricBucket> wrap = data.getPreviousWindow();
        if (wrap == null) {
            return 0;
        }
        return wrap.value().block();
    }

    /**
     * 获取前一个窗口的通过请求数。
     *
     * @return 前一个窗口的通过请求数。
     */
    @Override
    public long previousWindowPass() {
        data.currentWindow();
        WindowWrap<MetricBucket> wrap = data.getPreviousWindow();
        if (wrap == null) {
            return 0;
        }
        return wrap.value().pass();
    }
    /**
     * 对特定事件类型在当前窗口中增加计数。
     *
     * @param event 事件类型。
     * @param count 要增加的计数值。
     */
    public void add(MetricEvent event, long count) {
        data.currentWindow().value().add(event, count);
    }

    /**
     * 获取当前窗口中特定事件类型的计数。
     *
     * @param event 事件类型。
     * @return 当前窗口中该事件类型的计数。
     */
    public long getCurrentCount(MetricEvent event) {
        return data.currentWindow().value().get(event);
    }

    /**
     * Get total sum for provided event in {@code intervalInSec}.
     * 计算给定事件类型在所有窗口中的总和。
     * @param event event to calculate 需要计算总和的事件类型。
     * @return total sum for event 所有窗口中该事件类型的总和。
     */
    public long getSum(MetricEvent event) {
        data.currentWindow();
        long sum = 0;

        List<MetricBucket> buckets = data.values();
        for (MetricBucket bucket : buckets) {
            sum += bucket.get(event);
        }
        return sum;
    }

    /**
     * Get average count for provided event per second.
     * 计算给定事件类型每秒的平均计数。
     * @param event event to calculate
     * @return average count per second for event
     */
    public double getAvg(MetricEvent event) {
        return getSum(event) / data.getIntervalInSecond();
    }

    /**
     * 获取指定时间窗口的通过请求数。
     *
     * @param timeMillis 指定的时间窗口开始时间，以毫秒为单位。
     * @return 指定时间窗口的通过请求数。
     */
    @Override
    public long getWindowPass(long timeMillis) {
        MetricBucket bucket = data.getWindowValue(timeMillis);
        if (bucket == null) {
            return 0L;
        }
        return bucket.pass();
    }

    /**
     * 获取当前的等待队列长度。
     *
     * @return 当前的等待队列长度。
     */
    @Override
    public long waiting() {
        return data.currentWaiting();
    }

    /**
     * 获取窗口间隔，以秒为单位。
     *
     * @return 窗口间隔，以秒为单位。
     */
    @Override
    public double getWindowIntervalInSec() {
        return data.getIntervalInSecond();
    }

    /**
     * 获取样本计数，即每个窗口包含的样本数。
     *
     * @return 样本计数。
     */
    @Override
    public int getSampleCount() {
        return data.getSampleCount();
    }
}
