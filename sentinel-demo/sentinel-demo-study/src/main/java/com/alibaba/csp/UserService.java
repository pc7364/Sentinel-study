package com.alibaba.csp;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Service;

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

}
