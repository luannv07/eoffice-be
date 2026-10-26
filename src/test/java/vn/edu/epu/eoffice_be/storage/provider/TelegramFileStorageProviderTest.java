package vn.edu.epu.eoffice_be.storage.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import vn.edu.epu.eoffice_be.config.TelegramStorageProperties;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramFileStorageProviderTest {
    private TelegramFileStorageProvider provider;
    private StoredFileMetadataRepository repository;
    private MockRestServiceServer server;
    private TelegramStorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TelegramStorageProperties();
        properties.setChatbotHttpApi("synthetic-test-token");
        properties.setChannelId("-1000001");
        repository = mock(StoredFileMetadataRepository.class);
        provider = new TelegramFileStorageProvider(properties, repository);
        RestTemplate client = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        server = MockRestServiceServer.bindTo(client).build();
    }

    @Test
    void documentUploadReturnsReceiptWithoutWritingMetadata() {
        expectUpload("shared-file", 88);

        StoredFile receipt = provider.saveForDocument(pdf());

        assertEquals("shared-file", receipt.getId());
        assertEquals("88", receipt.getMetadata().get("messageId"));
        assertEquals("-1000001", receipt.getMetadata().get("chatId"));
        assertNull(receipt.getContent());
        verifyNoInteractions(repository);
        server.verify();
    }

    @Test
    void existingAttachmentUploadStillPersistsMetadata() {
        expectUpload("shared-file", 12);
        when(repository.findByProviderAndFileId("telegram", "shared-file"))
                .thenReturn(Optional.empty());

        provider.save(pdf());

        verify(repository).save(argThat(value ->
                "shared-file".equals(value.getFileId())
                        && "12".equals(value.getMetadata().get("messageId"))));
        server.verify();
    }

    @Test
    void cleanupUsesExactNewMessageNotOldMessageWithSameFileId() {
        expectUpload("shared-file", 12);
        expectUpload("shared-file", 88);
        server.expect(requestTo("https://api.telegram.org/botsynthetic-test-token/deleteMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("chat_id=-1000001&message_id=88"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));
        when(repository.findByProviderAndFileId("telegram", "shared-file"))
                .thenReturn(Optional.of(StoredFileMetadata.builder()
                        .provider("telegram").fileId("shared-file")
                        .metadata(Map.of("messageId", "12")).build()));
        provider.save(pdf());
        StoredFile receipt = provider.saveForDocument(pdf());
        clearInvocations(repository);
        properties.setChannelId("-1009999");

        provider.discardUpload(receipt);

        verifyNoInteractions(repository);
        server.verify();
    }

    @Test
    void cleanupFailureIsReportedToCaller() {
        server.expect(requestTo("https://api.telegram.org/botsynthetic-test-token/deleteMessage"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));
        assertThrows(FileStorageException.class, () -> provider.discardUpload(StoredFile.builder()
                .id("file").metadata(Map.of("messageId", "88", "chatId", "-1000001")).build()));
        server.verify();
    }

    @Test
    void invalidUploadResponseDoesNotWriteMetadata() {
        expectUpload("", 0);
        assertThrows(FileStorageException.class, () -> provider.saveForDocument(pdf()));
        verifyNoInteractions(repository);
        server.verify();
    }

    @Test
    void rejectedUploadDoesNotWriteMetadata() {
        server.expect(requestTo("https://api.telegram.org/botsynthetic-test-token/sendDocument"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));
        assertThrows(FileStorageException.class, () -> provider.saveForDocument(pdf()));
        verifyNoInteractions(repository);
        server.verify();
    }

    @Test
    void invalidReceiptNeverSendsDeleteRequest() {
        assertThrows(FileStorageException.class, () -> provider.discardUpload(StoredFile.builder()
                .id("file").metadata(Map.of("messageId", "0", "chatId", "-1000001")).build()));
        verifyNoInteractions(repository);
        server.verify();
    }

    private void expectUpload(String fileId, long messageId) {
        server.expect(requestTo("https://api.telegram.org/botsynthetic-test-token/sendDocument"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":%d,"document":{"file_id":"%s","file_size":12}}}
                        """.formatted(messageId, fileId), MediaType.APPLICATION_JSON));
    }

    private StoredFile pdf() {
        return StoredFile.builder().originalFileName("signed.pdf").contentType("application/pdf")
                .content("%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).build();
    }
}
