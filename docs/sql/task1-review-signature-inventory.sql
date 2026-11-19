-- Liệt kê hồ sơ đang thẩm định có log ký nháy cũ thiếu liên kết yêu cầu.
SELECT d.id AS document_id,
       l.id AS workflow_log_id,
       l.actor_id,
       l.signature_hash
FROM documents d
JOIN document_workflow_logs l ON l.document_id = d.id
WHERE d.delete_flg = false
  AND d.status = 'IN_REVIEW'
  AND l.delete_flg = false
  AND l.action = 'INITIAL_SIGN'
  AND l.from_status = 'IN_REVIEW'
  AND l.to_status = 'IN_REVIEW'
  AND l.approval_request_id IS NULL
ORDER BY d.id, l.id;
