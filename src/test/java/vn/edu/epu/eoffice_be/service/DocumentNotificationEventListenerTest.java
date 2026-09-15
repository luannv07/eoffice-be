package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocumentNotificationEventListenerTest {

    @Test
    @DisplayName("Nhan su kien DocumentSubmittedEvent khong nem ngoai le")
    void onDocumentSubmitted_Success() {
        DocumentNotificationEventListener listener = new DocumentNotificationEventListener();
        DocumentSubmittedEvent event = new DocumentSubmittedEvent(
                this, 1L, 10L, 20L, "Bao cao tai chinh", "Ghi chu"
        );

        assertDoesNotThrow(() -> listener.onDocumentSubmitted(event));
    }
}
