package vn.edu.epu.eoffice_be.event;

/** A shared destination: deliberately contains no individual clerk ID. */
public record DocumentQueuedForClerksEvent(Long documentId, Long senderUserId, String title, String note) {}
