package com.multichat.audit.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.dto.AuditPage;
import com.multichat.audit.service.dto.AuditQuery;

public interface AuditService {
    void append(AuditLog auditLog);

    AuditPage query(AuditQuery query);
}
