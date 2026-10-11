package com.health.controller;

import com.health.common.utils.Result;
import com.health.service.DepartmentCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DepartmentController {
    private final DepartmentCatalog catalog;
    public DepartmentController(DepartmentCatalog catalog) { this.catalog = catalog; }

    /** 医生注册页尚未登录，需要公开获取科室。 */
    @GetMapping("/api/doctor/departments")
    public Result<List<String>> departments() { return Result.success(catalog.names()); }
}
