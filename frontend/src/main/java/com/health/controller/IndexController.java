package com.health.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    @GetMapping(value = {"/", "/index.html", "/app/**"})
    public String index() {
        return "forward:/index.html";
    }
}
