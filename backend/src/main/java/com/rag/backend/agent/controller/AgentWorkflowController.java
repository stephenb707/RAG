package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.*;
import com.rag.backend.agent.workflow.AgentChangeWorkflowService;
import com.rag.backend.agent.workflow.AgentLoopService;
import com.rag.backend.agent.workflow.AgentProposalWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/workflow")
public class AgentWorkflowController {

    private final AgentChangeWorkflowService changeWorkflowService;
    private final AgentLoopService agentLoopService;
    private final AgentProposalWorkflowService proposalWorkflowService;

    public AgentWorkflowController(
            AgentChangeWorkflowService changeWorkflowService,
            AgentLoopService agentLoopService,
            AgentProposalWorkflowService proposalWorkflowService
    ) {
        this.changeWorkflowService = changeWorkflowService;
        this.agentLoopService = agentLoopService;
        this.proposalWorkflowService = proposalWorkflowService;
    }

    @PostMapping("/change")
    public ResponseEntity<AgentChangeWorkflowResponse> runChangeWorkflow(@RequestBody AgentChangeWorkflowRequest request) throws IOException {
        return ResponseEntity.ok(changeWorkflowService.run(request));
    }

    @PostMapping("/propose")
    public ResponseEntity<AgentProposalResponse> propose(@RequestBody AgentProposeWorkflowRequest request) throws Exception {
        return ResponseEntity.ok(proposalWorkflowService.propose(request));
    }

    @PostMapping("/apply")
    public ResponseEntity<AgentApplyProposalResponse> apply(@RequestBody AgentApplyProposalRequest request) {
        return ResponseEntity.ok(proposalWorkflowService.apply(request));
    }

    @GetMapping("/propose/diag")
    public ResponseEntity<Map<String, Object>> proposeDiag() {
        return ResponseEntity.ok(Map.of(
                "proposeEndpoint", "POST /api/agent/workflow/propose",
                "applyEndpoint", "POST /api/agent/workflow/apply"
        ));
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
