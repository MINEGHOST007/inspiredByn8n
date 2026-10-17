package com.w6w.runtime.actions;

import java.util.List;

public record WorkflowEmailMessage(String from, List<String> to, List<String> cc, String subject, String body) {}
