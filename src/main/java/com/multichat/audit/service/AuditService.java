package com.multichat.audit.service;

import com.multichat.audit.entity.AuditLog;

public interface AuditService {
    void append(AuditLog auditLog);
}
