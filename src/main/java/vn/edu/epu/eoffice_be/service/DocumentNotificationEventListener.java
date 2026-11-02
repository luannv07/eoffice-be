package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent;
import vn.edu.epu.eoffice_be.event.DocumentQueuedForClerksEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentNotificationEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentSubmitted(DocumentSubmittedEvent event) {
        log.info("[NOTIFICATION] Nhận sự kiện gửi duyệt văn bản: docId={}, senderId={}, approverId={}",
                event.getDocumentId(), event.getSenderUserId(), event.getApproverUserId());
        // Sẵn sàng kết nối tới UC18 (Quản lý thông báo hệ thống / WebSocket / Email)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentQueuedForClerks(DocumentQueuedForClerksEvent event) {
        log.info("[NOTIFICATION] Văn bản vào hàng chờ văn thư: docId={}, senderId={}",
                event.documentId(), event.senderUserId());
    }
}
