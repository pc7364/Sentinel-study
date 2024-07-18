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

import java.util.HashSet;
import java.util.Set;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * <p>
 * A {@link Node} used to hold statistics for specific resource name in the specific context.
 * Each distinct resource in each distinct {@link Context} will corresponding to a {@link DefaultNode}.
 *  一个 {@link Node}，用于存储特定上下文中特定资源名称的统计信息。
 *  每个不同上下文中的每个不同资源都将对应一个 {@link DefaultNode}。
 * </p>
 * <p>
 * This class may have a list of sub {@link DefaultNode}s. Child nodes will be created when
 * calling {@link SphU}#entry() or {@link SphO}@entry() multiple times in the same {@link Context}.
 * 本类可能包含一系列子 {@link DefaultNode}。当在相同的 {@link Context} 中多次调用
 * {@link SphU}#entry() 或 {@link SphO}#entry() 时，将创建子节点。
 * </p>
 *
 * @author qinan.qn
 * @see NodeSelectorSlot
 */
// 用于统计特定资源在特定上下文中的使用情况。每个不同的资源在每个不同的上下文中都会对应一个 DefaultNode 对象。
// 该类可以拥有子节点列表，当在同一个上下文中多次调用 SphU.entry() 或 SphO.entry() 方法时，会创建子节点。
// DefaultNode 继承自 StatisticNode 类，包含了资源标识符 id、子节点列表 childList 和簇点 clusterNode 等属性。
// 该类提供了增加子节点、重置子节点列表、增加阻塞请求数、增加异常请求数、增加通过请求数、增加线程数、减少线程数等方法。
public class DefaultNode extends StatisticNode {

    /**
     * The resource associated with the node.
     * 节点所关联的数据
     */
    private ResourceWrapper id;

    /**
     * The list of all child nodes.
     * 所有子节点的集合。
     */
    private volatile Set<Node> childList = new HashSet<>();

    /**
     * Associated cluster node.
     * 关联的集群节点。
     */
    private ClusterNode clusterNode;

    /**
     * 创建一个新的DefaultNode实例。
     *
     * @param id         节点所关联的资源。
     * @param clusterNode 关联的集群节点。
     */
    public DefaultNode(ResourceWrapper id, ClusterNode clusterNode) {
        this.id = id;
        this.clusterNode = clusterNode;
    }

    public ResourceWrapper getId() {
        return id;
    }

    public ClusterNode getClusterNode() {
        return clusterNode;
    }

    public void setClusterNode(ClusterNode clusterNode) {
        this.clusterNode = clusterNode;
    }

    /**
     * Add child node to current node.
     *
     * @param node valid child node
     */
    public void addChild(Node node) {
        if (node == null) {
            // 如果尝试添加的子节点为null，则记录警告日志并返回。
            RecordLog.warn("Trying to add null child to node <{}>, ignored", id.getName());
            return;
        }
        // 如果当前节点的子节点列表中不包含该节点，则将其添加进子节点列表。
        if (!childList.contains(node)) {
            synchronized (this) {
                if (!childList.contains(node)) {
                    // 以线程安全的方式更新子节点列表，避免并发修改异常。
                    Set<Node> newSet = new HashSet<>(childList.size() + 1);
                    newSet.addAll(childList);
                    newSet.add(node);
                    childList = newSet;
                }
            }
            RecordLog.info("Add child <{}> to node <{}>", ((DefaultNode) node).id.getName(), id.getName());
        }
    }

    /**
     * Reset the child node list.
     */
    public void removeChildList() {
        this.childList = new HashSet<>();
    }

    public Set<Node> getChildList() {
        return childList;
    }

    /**
     * 增加节点的阻塞请求数。
     * @param count 需要增加的阻塞请求数量。
     */
    @Override
    public void increaseBlockQps(int count) {
        super.increaseBlockQps(count);
        this.clusterNode.increaseBlockQps(count);
    }

    /**
     * 增加节点的异常请求数。
     * @param count 需要增加的异常请求数量。
     */
    @Override
    public void increaseExceptionQps(int count) {
        super.increaseExceptionQps(count);
        this.clusterNode.increaseExceptionQps(count);
    }

    /**
     * 增加节点的通过请求数和请求响应时间。
     *
     * @param rt    请求响应时间，单位为毫秒。
     * @param successCount 通过的请求数量。
     */
    @Override
    public void addRtAndSuccess(long rt, int successCount) {
        super.addRtAndSuccess(rt, successCount);
        this.clusterNode.addRtAndSuccess(rt, successCount);
    }

    /**
     * 增加当前节点的线程数量。
     */
    @Override
    public void increaseThreadNum() {
        super.increaseThreadNum();
        this.clusterNode.increaseThreadNum();
    }

    /**
     * 减少当前节点的线程数量。
     */
    @Override
    public void decreaseThreadNum() {
        super.decreaseThreadNum();
        this.clusterNode.decreaseThreadNum();
    }

    /**
     * 增加节点的通过请求数。
     * @param count 需要增加的通过请求数量。
     */
    @Override
    public void addPassRequest(int count) {
        super.addPassRequest(count);
        this.clusterNode.addPassRequest(count);
    }

    public void printDefaultNode() {
        visitTree(0, this);
    }

    private void visitTree(int level, DefaultNode node) {
        for (int i = 0; i < level; ++i) {
            System.out.print("-");
        }
        if (!(node instanceof EntranceNode)) {
            System.out.println(
                String.format("%s(thread:%s pq:%s bq:%s tq:%s rt:%s 1mp:%s 1mb:%s 1mt:%s)", node.id.getShowName(),
                    node.curThreadNum(), node.passQps(), node.blockQps(), node.totalQps(), node.avgRt(),
                    node.totalRequest() - node.blockRequest(), node.blockRequest(), node.totalRequest()));
        } else {
            System.out.println(
                String.format("Entry-%s(t:%s pq:%s bq:%s tq:%s rt:%s 1mp:%s 1mb:%s 1mt:%s)", node.id.getShowName(),
                    node.curThreadNum(), node.passQps(), node.blockQps(), node.totalQps(), node.avgRt(),
                    node.totalRequest() - node.blockRequest(), node.blockRequest(), node.totalRequest()));
        }
        for (Node n : node.getChildList()) {
            DefaultNode dn = (DefaultNode)n;
            visitTree(level + 1, dn);
        }
    }

}
