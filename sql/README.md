# PostgreSQL migrations

Run the files in `migrations/` in lexical order, once per database. They target PostgreSQL 14 or later and use `pgcrypto` for UUID defaults.

```powershell
Get-ChildItem .\sql\migrations\V*.sql | Sort-Object Name | ForEach-Object {
  psql --set ON_ERROR_STOP=1 --file $_.FullName $env:DATABASE_URL
}
```

`V004__demo_data.sql` is development seed data, not production data. The three demo password hashes are deliberately invalid placeholders. `V011__default_system_administrator.sql` creates the development bootstrap account `admin` / `Admin123!` with the `SYSTEM_ADMIN` role; change its password before any non-development deployment.

After migrating the development database, run `psql --set ON_ERROR_STOP=1 --file .\sql\verification\verify_core_invariants.sql $env:DATABASE_URL` to check the main database-enforced invariants. It runs inside a transaction and rolls back.

## Transactional write contract

`messages.request_id` is a client-generated UUID. A retry must send the same value; `UNIQUE (sender_id, request_id)` makes the second insert fail deterministically, after which the service reads and returns the existing message.

Do not provide `room_seq` or `notification_seq` from the application. The `BEFORE INSERT` trigger locks and increments the corresponding `chat_rooms` counter in the same transaction. Normal messages receive `room_seq`; `SYSTEM_NOTIFICATION` receives only `notification_seq` and is immediately published.

For approval, rejection, timeout, and room deletion, use one transaction: conditionally update the message with `WHERE id = :id AND status = 'PENDING_REVIEW' AND version = :version`, append its audit log, then call `SELECT * FROM drain_room_publish_queue(:room_id)`. The function locks the room's `next_publish_seq`, publishes only contiguous `APPROVED` rows, skips rejected/timeout/cancelled rows, and returns the messages ready for at-least-once delivery.

All mutable tables use `version`. Updates must increment it by exactly one; stale writes are rejected by the database trigger. `audit_logs` is append-only. `users` and `chat_rooms` use `deleted_at` for logical deletion; memberships, messages, grants, and audit facts are retained as historical records rather than soft-deleted.
