/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.pojo;

/**
 * Result object for terminal command execution.
 */
public record ToolOutputResult(
        String output,
        String error,
        int exitCode,
        boolean isError,
        String errorMessage
) {
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        
        if (isError && errorMessage != null) {
            result.append("ERROR: ").append(errorMessage).append("\n\n");
        }
        
        if (output != null && !output.isEmpty()) {
            result.append("Output:\n").append(output);
        }
        
        if (error != null && !error.isEmpty()) {
            result.append("\nError Stream:\n").append(error);
        }
        
        result.append("\nExit Code: ").append(exitCode);
        
        return result.toString();
    }
}
