package org.example.llm.controller;

import org.example.llm.core.DyThreadPool;
import org.example.llm.core.ThreadPoolManager;
import org.example.llm.entity.ThreadPoolStatus;
import org.example.llm.service.ThreadService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/thread-pool-test")
public class ThreadPoolController {
    @Autowired
    private ThreadService threadService;



}
