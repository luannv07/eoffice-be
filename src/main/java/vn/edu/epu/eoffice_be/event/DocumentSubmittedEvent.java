package vn.edu.epu.eoffice_be.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DocumentSubmittedEvent extends ApplicationEvent {

    private final Long documentId;
    private final Long senderUserId;
    private final Long approverUserId;
    private final String documentTitle;
    private final String submitNote;

    public DocumentSubmittedEvent(Object source, Long documentId, Long senderUserId, Long approverUserId,
                                  String documentTitle, String submitNote) {
        super(source);
        this.documentId = documentId;
        this.senderUserId = senderUserId;
        this.approverUserId = approverUserId;
        this.documentTitle = documentTitle;
        this.submitNote = submitNote;
    }
}
