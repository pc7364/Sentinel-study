package com.alibaba.csp;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @Desc
 * @Author pc
 * @Date 2024/7/3 19:49
 */
@Service
public class UserService {

    @SentinelResource(value = "getUserName", blockHandler = "getUserNameBlockHandler")
    public String getUserName() {
        return "张三";
    }

    public String getUserNameBlockHandler(BlockException e) {
        return "被限流了啊";
    }

    @SentinelResource(value = "getUser", blockHandler = "getUserNameBlockHandler")
    public String getUser(int id , String name) {
        return "张三";
    }


    @PostConstruct
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        //设置受保护的资源
        rule.setResource("getUserName");
        // 设置流控规则 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 设置受保护的资源阈值
        rule.setCount(1);
        rule.setStrategy(2);
        rule.setRefResource("GET:/test1");
        rules.add(rule);
        // 加载配置好的规则
        FlowRuleManager.loadRules(rules);
    }
}
