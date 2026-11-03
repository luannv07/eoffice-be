-- READ ONLY. Verify the target database/schema before running.
-- Every result is a candidate for human verification, not authorization to change data.
-- Rows repeat for each attachment / return-log combination to preserve the evidence.
-- A retired main has is_main_document=false, so deleted rows cannot prove their former role.
WITH candidates AS (
    SELECT d.id, d.title, d.status, d.submitted_at, d.primary_file_path
    FROM documents d
    WHERE d.delete_flg = false
      AND (d.status IN ('NEEDS_REVISION', 'REJECTED')
           OR (d.status = 'DRAFT' AND (d.submitted_at IS NOT NULL
               OR EXISTS (SELECT 1 FROM document_approval_requests r WHERE r.document_id = d.id))))
), return_history AS (
    SELECT p.document_id, 'PROCESS' AS history_source, p.id AS history_id,
           p.action, p.log_time AS returned_at, p.delete_flg AS history_deleted
    FROM document_process_logs p
    WHERE p.action = 'RECALL_APPROVAL'
    UNION ALL
    SELECT w.document_id, 'WORKFLOW' AS history_source, w.id AS history_id,
           w.action, w.created_date AS returned_at, w.delete_flg AS history_deleted
    FROM document_workflow_logs w
    WHERE w.action = 'REQUEST_REVISION'
)
SELECT c.id AS document_id, c.title, c.status, c.submitted_at, c.primary_file_path,
       a.id AS attachment_id, a.original_file_name, a.file_extension,
       a.is_main_document AS attachment_is_main, a.delete_flg AS attachment_deleted,
       a.created_date AS attachment_created_at,
       h.history_source, h.history_id, h.action AS return_action,
       h.returned_at, h.history_deleted,
       'REQUIRES_VERIFICATION' AS assessment
FROM candidates c
LEFT JOIN document_attachments a ON a.document_id = c.id
LEFT JOIN return_history h ON h.document_id = c.id
ORDER BY c.id, a.id, h.returned_at, h.history_source, h.history_id;
