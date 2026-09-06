-- Supports the system-administrator audit explorer while retaining append-only history.
CREATE INDEX ix_audit_logs_action_created_at ON audit_logs (action, created_at DESC);
