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
package com.alibaba.csp.sentinel.node;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;
import com.alibaba.csp.sentinel.slots.statistic.metric.Metric;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * <p>The statistic node keep three kinds of real-time statistics metrics:</p>
 * <ol>
 * <li>metrics in second level ({@code rollingCounterInSecond})</li>
 * <li>metrics in minute level ({@code rollingCounterInMinute})</li>
 * <li>thread count</li>
 * </ol>
 *
 * <p>
 * Sentinel use sliding window to record and count the resource statistics in real-time.
 * The sliding window infrastructure behind the {@link ArrayMetric} is {@code LeapArray}.
 * </p>
 *
 * <p>
 * case 1: When the first request comes in, Sentinel will create a new window bucket of
 * a specified time-span to store running statics, such as total response time(rt),
 * incoming request(QPS), block request(bq), etc. And the time-span is defined by sample count.
 * </p>
 * <pre>
 * 	0      100ms
 *  +-------+--→ Sliding Windows
 * 	    ^
 * 	    |
 * 	  request
 * </pre>
 * <p>
 * Sentinel use the statics of the valid buckets to decide whether this request can be passed.
 * For example, if a rule defines that only 100 requests can be passed,
 * it will sum all qps in valid buckets, and compare it to the threshold defined in rule.
 * </p>
 *
 * <p>case 2: continuous requests</p>
 * <pre>
 *  0    100ms    200ms    300ms
 *  +-------+-------+-------+-----→ Sliding Windows
 *                      ^
 *                      |
 *                   request
 * </pre>
 *
 * <p>case 3: requests keeps coming, and previous buckets become invalid</p>
 * <pre>
 *  0    100ms    200ms	  800ms	   900ms  1000ms    1300ms
 *  +-------+-------+ ...... +-------+-------+ ...... +-------+-----→ Sliding Windows
 *                                                      ^
 *                                                      |
 *                                                    request
 * </pre>
 *
 * <p>The sliding window should become:</p>
 * <pre>
 * 300ms     800ms  900ms  1000ms  1300ms
 *  + ...... +-------+ ...... +-------+-----→ Sliding Windows
 *                                                      ^
 *                                                      |
 *                                                    request
 * </pre>
 *
 * @author qinan.qn
 * @author jialiang.linjl
 *
 * 此统计节点维护了三种实时统计指标：
 *      秒级指标 (rollingCounterInSecond)：跟踪每秒内的资源使用情况。
 *      分钟级指标 (rollingCounterInMinute)：记录每分钟内的资源使用状况。
 *      线程计数：监控当前正在执行的线程数量。
 * 实现机制
 *      Sentinel运用滑动窗口算法实时记录和计算资源的统计信息。滑动窗口的基础架构是LeapArray，它在ArrayMetric中实现。
 * 滑动窗口工作流程
 *
 * 案例1：首个请求到达
 *      当第一个请求到来时，Sentinel创建一个新的窗口，这个窗口有特定的时间跨度，用于存储运行时统计信息，比如总响应时间、入站请求（QPS）、阻塞请求等。时间跨度由采样次数确定。
 * 案例2：连续请求
 *      随着连续请求的不断到来，Sentinel在滑动窗口中记录这些请求的统计信息，保持数据的实时性和有效性。
 * 案例3：请求持续，旧窗口失效
 *      当请求持续涌入，且先前的窗口由于时间流逝而变得无效时，滑动窗口会自动移除这些旧窗口，同时保留最新的统计信息。
 * 决策过程
 *      Sentinel根据有效窗口中的统计信息来决定是否允许请求通过。例如，如果规则设定只允许100个请求通过，Sentinel会计算所有有效窗口中的QPS总和，并与规则中定义的阈值进行比较，从而决定是否放行新的请求。
 * 总结
 *      该功能通过滑动窗口机制实时监控资源使用情况，为流量控制、熔断等策略提供决策依据。
 */
/**
 * 该类的主要功能包括：
 *      维护三种实时统计指标：秒级指标（rollingCounterInSecond）、分钟级指标（rollingCounterInMinute）和线程计数（curThreadNum）。
 *      提供方法来获取和更新统计指标，如addPassRequest用于增加通过的请求数，addRtAndSuccess用于增加成功请求数和响应时间，increaseBlockQps用于增加阻塞请求数等。
 *      提供方法来重置统计信息，如reset方法。
 *      提供方法来获取特定时间范围内的指标详情，如metrics方法用于获取最近60秒内的指标详情。
 */
//这个类主要用于统计资源的使用情况，包括每秒请求数（QPS）、阻塞请求数、成功请求数、异常请求数、平均响应时间等指标。
//通过滑动窗口算法，StatisticNode可以实时记录和计算资源的统计信息，为流量控制、熔断等策略提供决策依据。
public class StatisticNode implements Node {

    /**
     * Holds statistics of the recent {@code INTERVAL} milliseconds. The {@code INTERVAL} is divided into time spans
     * by given {@code sampleCount}.
     * 秒级统计指标，用于记录最近INTERVAL毫秒内的统计信息。
     * 秒级的滑动时间窗口（时间窗口单位500ms）
     */
    private transient volatile Metric rollingCounterInSecond = new ArrayMetric(SampleCountProperty.SAMPLE_COUNT,
        IntervalProperty.INTERVAL);

    /**
     * Holds statistics of the recent 60 seconds. The windowLengthInMs is deliberately set to 1000 milliseconds,
     * meaning each bucket per second, in this way we can get accurate statistics of each second.
     * 分钟级统计指标，用于记录最近60秒内的统计信息。
     * 分钟级的滑动时间窗口（时间窗口单位1s）
     */
    private transient Metric rollingCounterInMinute = new ArrayMetric(60, 60 * 1000, false);

    /**
     * The counter for thread count.
     * 当前线程数计数器
     */
    private LongAdder curThreadNum = new LongAdder();

    /**
     * The last timestamp when metrics were fetched.
     * 上次获取统计指标的时间戳
     */
    private long lastFetchTime = -1;

    @Override
    public Map<Long, MetricNode> metrics() {
        // The fetch operation is thread-safe under a single-thread scheduler pool.
        // 获取当前时间并按秒取整
        long currentTime = TimeUtil.currentTimeMillis();
        currentTime = currentTime - currentTime % 1000;
        Map<Long, MetricNode> metrics = new ConcurrentHashMap<>();
        List<MetricNode> nodesOfEverySecond = rollingCounterInMinute.details();
        long newLastFetchTime = lastFetchTime;
        // Iterate metrics of all resources, filter valid metrics (not-empty and up-to-date).
        // 遍历所有节点，筛选出有效的时间段内的节点
        for (MetricNode node : nodesOfEverySecond) {
            if (isNodeInTime(node, currentTime) && isValidMetricNode(node)) {
                metrics.put(node.getTimestamp(), node);
                newLastFetchTime = Math.max(newLastFetchTime, node.getTimestamp());
            }
        }
        lastFetchTime = newLastFetchTime;

        return metrics;
    }

    /**
     * 根据条件获取分钟级统计指标的原始数据。
     *
     * @param timePredicate 时间判断谓词。
     * @return 满足条件的MetricNode列表。
     */
    @Override
    public List<MetricNode> rawMetricsInMin(Predicate<Long> timePredicate) {
        return rollingCounterInMinute.detailsOnCondition(timePredicate);
    }

    /**
     * 判断节点是否在指定的时间范围内且为有效的统计节点。
     *
     * @param node 要判断的节点。
     * @param currentTime 当前时间戳。
     * @return 如果节点在有效时间内且不为空，则返回true，否则返回false。
     */
    private boolean isNodeInTime(MetricNode node, long currentTime) {
        return node.getTimestamp() > lastFetchTime && node.getTimestamp() < currentTime;
    }

    /**
     * 判断MetricNode是否有效，即通过、阻塞、成功、异常QPS或RT大于0。
     *
     * @param node 要判断的MetricNode。
     * @return 如果节点有效，则返回true，否则返回false。
     */
    private boolean isValidMetricNode(MetricNode node) {
        return node.getPassQps() > 0 || node.getBlockQps() > 0 || node.getSuccessQps() > 0
                || node.getExceptionQps() > 0 || node.getRt() > 0 || node.getOccupiedPassQps() > 0;
    }

    /**
     * 重置统计信息。
     */
    @Override
    public void reset() {
        rollingCounterInSecond = new ArrayMetric(SampleCountProperty.SAMPLE_COUNT, IntervalProperty.INTERVAL);
    }

    /**
     * 获取总请求数（通过+阻塞）。
     *
     * @return 总请求数。
     */
    @Override
    public long totalRequest() {
        return rollingCounterInMinute.pass() + rollingCounterInMinute.block();
    }

    /**
     * 获取阻塞请求数。
     *
     * @return 阻塞请求数。
     */
    @Override
    public long blockRequest() {
        return rollingCounterInMinute.block();
    }

    /**
     * 获取当前秒级统计的阻塞QPS。
     *
     * @return 阻塞QPS。
     */
    @Override
    public double blockQps() {
        return rollingCounterInSecond.block() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取前一秒的阻塞QPS。
     *
     * @return 前一秒的阻塞QPS。
     */
    @Override
    public double previousBlockQps() {
        return this.rollingCounterInMinute.previousWindowBlock();
    }

    /**
     * 获取前一秒的通过QPS。
     *
     * @return 前一秒的通过QPS。
     */
    @Override
    public double previousPassQps() {
        return this.rollingCounterInMinute.previousWindowPass();
    }

    /**
     * 获取总QPS（通过+阻塞）。
     *
     * @return 总QPS。
     */
    @Override
    public double totalQps() {
        return passQps() + blockQps();
    }

    /**
     * 获取总成功请求数。
     *
     * @return 总成功请求数。
     */
    @Override
    public long totalSuccess() {
        return rollingCounterInMinute.success();
    }

    /**
     * 获取当前秒级统计的异常QPS。
     *
     * @return 异常QPS。
     */
    @Override
    public double exceptionQps() {
        return rollingCounterInSecond.exception() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取总异常请求数。
     *
     * @return 总异常请求数。
     */
    @Override
    public long totalException() {
        return rollingCounterInMinute.exception();
    }

    /**
     * 获取当前秒级统计的通过QPS。
     *
     * @return 通过QPS。
     */
    @Override
    public double passQps() {
        //计算每秒通过的请求数量（QPS）。
        // 具体实现是通过调用rollingCounterInSecond.pass()方法获取当前窗口内的请求数量，然后除以窗口的时间间隔（以秒为单位），得到每秒的请求数量。
        return rollingCounterInSecond.pass() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取总通过请求数。
     *
     * @return 总通过请求数。
     */
    @Override
    public long totalPass() {
        return rollingCounterInMinute.pass();
    }

    /**
     * 获取当前秒级统计的成功QPS。
     *
     * @return 成功QPS。
     */
    @Override
    public double successQps() {
        return rollingCounterInSecond.success() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取当前秒级统计的最大成功QPS。
     *
     * @return 最大成功QPS。
     */
    @Override
    public double maxSuccessQps() {
        return (double) rollingCounterInSecond.maxSuccess() * rollingCounterInSecond.getSampleCount()
                / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取当前秒级统计的已占用通过QPS。
     *
     * @return 已占用通过QPS。
     */
    @Override
    public double occupiedPassQps() {
        return rollingCounterInSecond.occupiedPass() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    /**
     * 获取平均响应时间。
     *
     * @return 平均响应时间（毫秒）。
     */
    @Override
    public double avgRt() {
        long successCount = rollingCounterInSecond.success();
        if (successCount == 0) {
            return 0;
        }

        return rollingCounterInSecond.rt() * 1.0 / successCount;
    }

    /**
     * 获取最小响应时间。
     *
     * @return 最小响应时间（毫秒）。
     */
    @Override
    public double minRt() {
        return rollingCounterInSecond.minRt();
    }

    /**
     * 获取当前线程数。
     *
     * @return 当前线程数。
     */
    @Override
    public int curThreadNum() {
        return (int)curThreadNum.sum();
    }

    /**
     * 增加通过请求数。
     *
     * @param count 增加的请求数。
     */
    @Override
    public void addPassRequest(int count) {
        rollingCounterInSecond.addPass(count);
        rollingCounterInMinute.addPass(count);
    }

    /**
     * 增加成功请求数和响应时间。
     *
     * @param rt 响应时间（毫秒）。
     * @param successCount 成功请求数。
     */
    @Override
    public void addRtAndSuccess(long rt, int successCount) {
        rollingCounterInSecond.addSuccess(successCount);
        rollingCounterInSecond.addRT(rt);

        rollingCounterInMinute.addSuccess(successCount);
        rollingCounterInMinute.addRT(rt);
    }

    /**
     * 增加阻塞请求数。
     *
     * @param count 增加的阻塞请求数。
     */
    @Override
    public void increaseBlockQps(int count) {
        rollingCounterInSecond.addBlock(count);
        rollingCounterInMinute.addBlock(count);
    }

    /**
     * 增加异常请求数。
     *
     * @param count 增加的异常请求数。
     */
    @Override
    public void increaseExceptionQps(int count) {
        rollingCounterInSecond.addException(count);
        rollingCounterInMinute.addException(count);
    }

    /**
     * 增加线程数。
     */
    @Override
    public void increaseThreadNum() {
        curThreadNum.increment();
    }

    /**
     * 减少线程数。
     */
    @Override
    public void decreaseThreadNum() {
        curThreadNum.decrement();
    }

    /**
     * 用于调试目的，打印当前统计信息。
     */
    @Override
    public void debug() {
        rollingCounterInSecond.debug();
    }

    /**
     * 尝试占用下一个时间窗口的请求数量。
     *
     * @param currentTime 当前时间戳。
     * @param acquireCount 尝试占用的请求数量。
     * @param threshold 通过阈值。
     * @return 如果可以占用，则返回等待时间；否则返回OccupiedTimeout。
     */
    @Override
    public long tryOccupyNext(long currentTime, int acquireCount, double threshold) {
        double maxCount = threshold * IntervalProperty.INTERVAL / 1000;
        long currentBorrow = rollingCounterInSecond.waiting();
        if (currentBorrow >= maxCount) {
            return OccupyTimeoutProperty.getOccupyTimeout();
        }

        int windowLength = IntervalProperty.INTERVAL / SampleCountProperty.SAMPLE_COUNT;
        long earliestTime = currentTime - currentTime % windowLength + windowLength - IntervalProperty.INTERVAL;

        int idx = 0;
        /*
         * Note: here {@code currentPass} may be less than it really is NOW, because time difference
         * since call rollingCounterInSecond.pass(). So in high concurrency, the following code may
         * lead more tokens be borrowed.
         */
        long currentPass = rollingCounterInSecond.pass();
        while (earliestTime < currentTime) {
            long waitInMs = idx * windowLength + windowLength - currentTime % windowLength;
            if (waitInMs >= OccupyTimeoutProperty.getOccupyTimeout()) {
                break;
            }
            long windowPass = rollingCounterInSecond.getWindowPass(earliestTime);
            if (currentPass + currentBorrow + acquireCount - windowPass <= maxCount) {
                return waitInMs;
            }
            earliestTime += windowLength;
            currentPass -= windowPass;
            idx++;
        }

        return OccupyTimeoutProperty.getOccupyTimeout();
    }

    /**
     * 获取等待中的请求数量。
     *
     * @return 等待中的请求数量。
     */
    @Override
    public long waiting() {
        return rollingCounterInSecond.waiting();
    }

    /**
     * 添加等待中的请求。
     *
     * @param futureTime 未来时间点。
     * @param acquireCount 请求的数量。
     */
    @Override
    public void addWaitingRequest(long futureTime, int acquireCount) {
        rollingCounterInSecond.addWaiting(futureTime, acquireCount);
    }

    /**
     * 添加已占用的通过请求数。
     *
     * @param acquireCount 已占用的请求数量。
     */
    @Override
    public void addOccupiedPass(int acquireCount) {
        rollingCounterInMinute.addOccupiedPass(acquireCount);
        rollingCounterInMinute.addPass(acquireCount);
    }
}
