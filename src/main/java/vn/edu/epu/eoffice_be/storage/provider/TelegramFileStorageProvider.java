package vn.edu.epu.eoffice_be.storage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import vn.edu.epu.eoffice_be.config.TelegramStorageProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TelegramFileStorageProvider implements FileStorageProvider {

    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final String FILE_BASE = "https://api.telegram.org/file/bot";

    private final TelegramStorageProperties properties;
    private final RestTemplate restTemplate;

    // Lưu tạm messageId theo fileId để hỗ trợ xóa (Telegram không có API xóa file)
    private final Map<String, Long> messageIdByFileId = new ConcurrentHashMap<>();

    public TelegramFileStorageProvider(TelegramStorageProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getName() {
        return "telegram";
    }

    @Override
    public StoredFile save(StoredFile file) {
        if (file == null || file.getContent() == null || file.getContent().length == 0) {
            throw new FileStorageException("Nội dung tệp không được để trống");
        }
        String token = yeuCauToken();
        String chatId = yeuCauChannelId();
        String url = API_BASE + token + "/sendDocument";

        ByteArrayResource resource = new ByteArrayResource(file.getContent()) {
            @Override
            public String getFilename() {
                return file.getOriginalFileName() != null ? file.getOriginalFileName() : "file";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("document", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        JsonNode root = restTemplate.postForObject(url, request, JsonNode.class);
        if (root == null || !root.path("ok").asBoolean(false)) {
            throw new FileStorageException(
                    "Gửi tệp lên Telegram thất bại: " + (root == null ? "không có phản hồi" : root.path("description").asText()));
        }

        JsonNode result = root.path("result");
        String fileId = result.path("document").path("file_id").asText();
        long messageId = result.path("message_id").asLong();
        long size = result.path("document").path("file_size").asLong(file.getContent().length);

        messageIdByFileId.put(fileId, messageId);

        return StoredFile.builder()
                .id(fileId)
                .originalFileName(file.getOriginalFileName())
                .contentType(file.getContentType())
                .sizeBytes(size)
                .metadata(Map.of("messageId", String.valueOf(messageId)))
                .build();
    }

    @Override
    public StoredFile get(String fileId) {
        String token = yeuCauToken();
        String filePath = giaiQuyetDuongDan(token, fileId);
        byte[] content = restTemplate.getForObject(FILE_BASE + token + "/" + filePath, byte[].class);
        if (content == null) {
            throw new FileStorageException("Telegram trả về nội dung rỗng cho fileId " + fileId);
        }
        return StoredFile.builder()
                .id(fileId)
                .content(content)
                .sizeBytes(content.length)
                .build();
    }

    @Override
    public StoredFile update(String fileId, StoredFile file) {
        // Telegram không hỗ trợ ghi đè: tải lên bản mới rồi xóa tin nhắn cũ
        StoredFile updated = save(file);
        delete(fileId);
        return updated;
    }

    @Override
    public void delete(String fileId) {
        String token = yeuCauToken();
        String chatId = yeuCauChannelId();
        Long messageId = messageIdByFileId.get(fileId);
        if (messageId == null) {
            log.warn("Không tìm thấy messageId lưu sẵn cho fileId {}, không thể xóa tin nhắn Telegram", fileId);
            return;
        }
        String url = API_BASE + token + "/deleteMessage?chat_id=" + chatId + "&message_id=" + messageId;
        try {
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root == null || !root.path("ok").asBoolean(false)) {
                log.warn("Xóa tin nhắn Telegram thất bại cho fileId {}", fileId);
            }
        } catch (Exception e) {
            log.warn("Lỗi xóa tin nhắn Telegram cho fileId {}: {}", fileId, e.getMessage());
        } finally {
            messageIdByFileId.remove(fileId);
        }
    }

    @Override
    public List<StoredFile> getAll() {
        return Collections.emptyList();
    }

    @Override
    public StoredFile getDetail(String fileId) {
        throw new UnsupportedOperationException("Provider Telegram không hỗ trợ getDetail");
    }

    @Override
    public void deleteMany(List<String> fileIds) {
        throw new UnsupportedOperationException("Provider Telegram không hỗ trợ deleteMany");
    }

    private String giaiQuyetDuongDan(String token, String fileId) {
        String url = API_BASE + token + "/getFile?file_id=" + fileId;
        JsonNode root = restTemplate.getForObject(url, JsonNode.class);
        if (root == null || !root.path("ok").asBoolean(false)) {
            throw new FileStorageException(
                    "Lấy thông tin tệp Telegram thất bại: " + (root == null ? "không có phản hồi" : root.path("description").asText()));
        }
        return root.path("result").path("file_path").asText();
    }

    private String yeuCauToken() {
        if (properties.getChatbotHttpApi() == null || properties.getChatbotHttpApi().isBlank()) {
            throw new FileStorageException(
                    "Chưa cấu hình token bot Telegram (file.storage.provider.telegram.chatbot-http-api)");
        }
        return properties.getChatbotHttpApi();
    }

    private String yeuCauChannelId() {
        if (properties.getChannelId() == null || properties.getChannelId().isBlank()) {
            throw new FileStorageException(
                    "Chưa cấu hình channel id Telegram (file.storage.provider.telegram.channel-id)");
        }
        return properties.getChannelId();
    }
}
