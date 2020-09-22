package com.james.demo.impl;

import com.james.annotation.JService;
import com.james.demo.IDemoService;

@JService
public class DemoServiceImpl implements IDemoService {
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}
