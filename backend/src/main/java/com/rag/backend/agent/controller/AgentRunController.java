package com.rag.backend.agent.controller;

import com.rag.backend.agent.run.AgentRunRecord;
import com.rag.backend.agent.run.AgentRunStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {

    private final AgentRunStoreService runStore;

    public AgentRunController(AgentRunStoreService runStore) {
        this.runStore = runStore;
    }

    @GetMapping("/{runId}")
    public ResponseEntity<AgentRunRecord> getRun(@PathVariable String runId) {
        if (runId == null || runId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AgentRunRecord record = runStore.getRun(runId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    @GetMapping
    public ResponseEntity<List<AgentRunRecord>> listRuns(
            @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        int capped = limit > 0 && limit <= 100 ? limit : 20;
        List<AgentRunRecord> runs = runStore.listRuns(capped);
        return ResponseEntity.ok(runs);
    }
}
