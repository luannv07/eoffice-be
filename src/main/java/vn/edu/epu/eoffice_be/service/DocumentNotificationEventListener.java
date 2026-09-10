package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentNotificationEventListener {

    @Async
    @EventListener
    public void onDocumentSubmitted(DocumentSubmittedEvent event) {
        log.info("[NOTIFICATION] Nhận sự kiện gửi duyệt văn bản: docId={}, senderId={}, approverId={}, title='{}'",
                event.getDocumentId(), event.getSenderUserId(), event.getApproverUserId(), event.getDocumentTitle());
        // Sẵn sàng kết nối tới UC18 (Quản lý thông báo hệ thống / WebSocket / Email)
    }
}
