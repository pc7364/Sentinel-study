package com.alibaba.csp.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @Desc
 * @Author pc
 * @Date 2024/7/3 10:49
 */
@RestController
public class SentinelResourceController {

    @RequestMapping("/annotationDemo")
    @SentinelResource(value = "annotationDemo",blockHandler = "handleException",
            fallback = "fallbackException")
    public String annotationDemo(){
        int i = 1/0;
        return "Annotation Demo 1";
    }

    // Block 异常处理函数
    public String handleException(BlockException ex){
        return "被流控了";
    }

    // Fallback 异常处理函数
    public String fallbackException(Throwable t){
        return "被异常降级了";
    }

    @PostConstruct
    private void initFlowRules(){
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        //设置受保护的资源
        rule.setResource("annotationDemo");
        // 设置流控规则 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 设置受保护的资源阈值
        rule.setCount(1);
        rules.add(rule);
        // 加载配置好的规则
        FlowRuleManager.loadRules(rules);
    }
}
