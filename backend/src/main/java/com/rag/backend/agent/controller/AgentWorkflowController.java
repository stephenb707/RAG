package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.AgentChangeWorkflowRequest;
import com.rag.backend.agent.dto.AgentChangeWorkflowResponse;
import com.rag.backend.agent.dto.AgentLoopRequest;
import com.rag.backend.agent.dto.AgentLoopResponse;
import com.rag.backend.agent.workflow.AgentChangeWorkflowService;
import com.rag.backend.agent.workflow.AgentLoopService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/workflow")
public class AgentWorkflowController {

    private final AgentChangeWorkflowService changeWorkflowService;
    private final AgentLoopService agentLoopService;

    public AgentWorkflowController(AgentChangeWorkflowService changeWorkflowService, AgentLoopService agentLoopService) {
        this.changeWorkflowService = changeWorkflowService;
        this.agentLoopService = agentLoopService;
    }

    @PostMapping("/change")
    public ResponseEntity<AgentChangeWorkflowResponse> runChangeWorkflow(@RequestBody AgentChangeWorkflowRequest request) throws IOException {
        return ResponseEntity.ok(changeWorkflowService.run(request));
    }

    @PostMapping("/loop")
    public ResponseEntity<AgentLoopResponse> runLoop(@RequestBody AgentLoopRequest request) throws IOException {
        return ResponseEntity.ok(agentLoopService.run(request));
    }

    @GetMapping("/loop/diag")
    public ResponseEntity<Map<String, Object>> loopDiag() {
        return ResponseEntity.ok(Map.of(
                "maxIterationsDefault", AgentLoopService.getDefaultMaxIterations(),
                "maxToolCallsDefault", AgentLoopService.getDefaultMaxToolCalls(),
                "maxTranscriptChars", AgentLoopService.getMaxTranscriptChars(),
                "maxFilesReadPerRun", AgentLoopService.getMaxFilesReadPerRun()
        ));
    }
}
