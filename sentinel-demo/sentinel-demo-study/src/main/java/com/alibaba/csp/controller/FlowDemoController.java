//package com.alibaba.csp.controller;
//
//import com.alibaba.csp.sentinel.Entry;
//import com.alibaba.csp.sentinel.SphU;
//import com.alibaba.csp.sentinel.slots.block.BlockException;
//import com.alibaba.csp.sentinel.slots.block.RuleConstant;
//import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
//import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.annotation.PostConstruct;
//import java.util.ArrayList;
//import java.util.List;
//
//@RestController
//public class FlowDemoController {
//    private static final String RESOURCE_NAME = "flowDemo";
//
//    @RequestMapping("/hello")
//    public String hello(){
//        try(Entry entry = SphU.entry(RESOURCE_NAME)){
//            //保护的资源
//            System.out.println("hello");
//            return "hello";
//        }catch (BlockException e){
//            return "被限流了";
//        }
//    }
//
//    @PostConstruct
//    private void initFlowRules(){
//        List<FlowRule> rules = new ArrayList<>();
//        FlowRule rule = new FlowRule();
//        //设置受保护的资源
//        rule.setResource(RESOURCE_NAME);
//        // 设置流控规则 QPS
//        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
//        // 设置受保护的资源阈值
//        rule.setCount(1);
//        rules.add(rule);
//        // 加载配置好的规则
//        FlowRuleManager.loadRules(rules);
//    }
//}
