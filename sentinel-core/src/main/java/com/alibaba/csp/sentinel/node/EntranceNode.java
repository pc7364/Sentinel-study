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

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * <p>
 * A {@link Node} represents the entrance of the invocation tree.
 * </p>
 * <p>
 * One {@link Context} will related to a {@link EntranceNode},
 * which represents the entrance of the invocation tree. New {@link EntranceNode} will be created if
 * current context does't have one. Note that same context name will share same {@link EntranceNode}
 * globally.
 * </p>
 *
 * @author qinan.qn
 * @see ContextUtil
 * @see ContextUtil#enter(String, String)
 * @see NodeSelectorSlot
 */
//用于表示调用链路的入口节点。每个入口节点对应一个上下文（Context），同一个上下文名称会在全局共享同一个入口节点。
public class EntranceNode extends DefaultNode {

    /**
     * 构造函数，创建一个入口节点。
     *
     * @param id 资源标识，用于唯一标识一个资源。
     * @param clusterNode 集群节点对象，用于统计集群中的流量数据。
     */
    public EntranceNode(ResourceWrapper id, ClusterNode clusterNode) {
        super(id, clusterNode);
    }

    /**
     * 计算平均响应时间（RT）。
     * 平均响应时间是所有子节点的平均响应时间乘以通过率的总和，除以所有子节点的通过率总和。
     * 如果所有子节点的通过率为0，则平均响应时间为0。
     *
     * @return 平均响应时间，单位为毫秒。
     */
    @Override
    public double avgRt() {
        double total = 0;
        double totalQps = 0;
        for (Node node : getChildList()) {
            total += node.avgRt() * node.passQps();
            totalQps += node.passQps();
        }
        return total / (totalQps == 0 ? 1 : totalQps);
    }

    /**
     * 计算阻塞请求数。
     * 阻塞请求数是所有子节点的阻塞请求数的总和。
     *
     * @return 阻塞请求数。
     */
    @Override
    public double blockQps() {
        double blockQps = 0;
        for (Node node : getChildList()) {
            blockQps += node.blockQps();
        }
        return blockQps;
    }

    /**
     * 计算阻塞请求的总次数。
     * 阻塞请求的总次数是所有子节点阻塞请求总次数的总和。
     *
     * @return 阻塞请求的总次数。
     */
    @Override
    public long blockRequest() {
        long r = 0;
        for (Node node : getChildList()) {
            r += node.blockRequest();
        }
        return r;
    }

    /**
     * 计算当前线程数。
     * 当前线程数是所有子节点当前线程数的总和。
     *
     * @return 当前线程数。
     */
    @Override
    public int curThreadNum() {
        int r = 0;
        for (Node node : getChildList()) {
            r += node.curThreadNum();
        }
        return r;
    }

    /**
     * 计算总请求数。
     * 总请求数是所有子节点总请求数的总和。
     *
     * @return 总请求数。
     */
    @Override
    public double totalQps() {
        double r = 0;
        for (Node node : getChildList()) {
            r += node.totalQps();
        }
        return r;
    }

    /**
     * 计算成功请求数。
     * 成功请求数是所有子节点成功请求数的总和。
     *
     * @return 成功请求数。
     */
    @Override
    public double successQps() {
        double r = 0;
        for (Node node : getChildList()) {
            r += node.successQps();
        }
        return r;
    }

    /**
     * 计算通过的请求数。
     * 通过的请求数是所有子节点通过的请求数的总和。
     *
     * @return 通过的请求数。
     */
    @Override
    public double passQps() {
        double r = 0;
        for (Node node : getChildList()) {
            r += node.passQps();
        }
        return r;
    }

    /**
     * 计算总请求次数。
     * 总请求次数是所有子节点总请求次数的总和。
     *
     * @return 总请求次数。
     */
    @Override
    public long totalRequest() {
        long r = 0;
        for (Node node : getChildList()) {
            r += node.totalRequest();
        }
        return r;
    }

    /**
     * 计算总通过次数。
     * 总通过次数是所有子节点总通过次数的总和。
     *
     * @return 总通过次数。
     */
    @Override
    public long totalPass() {
        long r = 0;
        for (Node node : getChildList()) {
            r += node.totalPass();
        }
        return r;
    }
}
