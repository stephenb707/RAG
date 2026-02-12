package com.rag.runner.controller;

import com.rag.runner.dto.ExecRunRequest;
import com.rag.runner.dto.ExecRunResponse;
import com.rag.runner.service.ExecService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exec")
public class ExecController {

    private final ExecService execService;

    public ExecController(ExecService execService) {
        this.execService = execService;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ExecRunResponse run(@RequestBody ExecRunRequest req) throws Exception {
        return execService.run(req);
    }
}
