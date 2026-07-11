package com.example.watermanagement.dto;

public record DatabaseRestoreStage(
        String token,
        String status,
        boolean desktopMode,
        String message) {
}
