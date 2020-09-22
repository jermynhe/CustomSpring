package com.james.demo;

import com.james.annotation.JAutowired;
import com.james.annotation.JController;
import com.james.annotation.JRequestMapping;
import com.james.annotation.JRequestParam;

@JController
public class DemoController {

    @JAutowired
    private IDemoService demoService;

    @JRequestMapping("say")
    public String say(@JRequestParam("name") String name) {
        return demoService.sayHello(name);
    }
}