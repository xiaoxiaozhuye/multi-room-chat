package com.multichat.audit.service.dto;

import com.multichat.audit.entity.AuditLog;

import java.util.List;

public record AuditPage(List<AuditLog> items, int page, int size, boolean hasNext) {
}
