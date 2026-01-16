package com.rag.backend.controller;

import com.rag.backend.dto.ChatRequest;
import com.rag.backend.dto.ChatResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = "Dummy response: " + request.getMessage();
        return new ChatResponse(reply);
    }
}
