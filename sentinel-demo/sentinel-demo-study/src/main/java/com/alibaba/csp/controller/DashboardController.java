package com.alibaba.csp.controller;

import com.alibaba.csp.UserService;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DashboardController {

    @RequestMapping("/test")
    public String test(){
        return "test";
    }


    @RequestMapping("/write")
    public String write(){
        return "write";
    }

    @RequestMapping("/read")
    public String read(){
        return "read";
    }


    @Resource
    private UserService userService;

    @RequestMapping("/test1")
    public String test1(){
        return userService.getUserName();
    }

    @RequestMapping("/test2")
    public String test2(){
        return userService.getUserName();
    }


    @RequestMapping("/testDegrade")
    public String testDegrade() {
        int i = 1/0;
        return "testDegrade";
    }


    @RequestMapping("/testParam")
    public String testParam(@RequestParam int id) {
        return userService.getUser(id , "张三");
    }



    @PostConstruct
    private void initFlowRules(){
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        //设置受保护的资源
        rule.setResource("GET:/test");
        // 设置流控规则 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 设置受保护的资源阈值
        rule.setCount(1);
        rules.add(rule);
        // 加载配置好的规则
        FlowRuleManager.loadRules(rules);
    }

}
