package com.rag.backend.dto;

import java.util.List;

public record RagAnswer(String answer, List<Citation> citations) {}
