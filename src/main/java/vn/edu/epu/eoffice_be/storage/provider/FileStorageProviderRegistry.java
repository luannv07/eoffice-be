package vn.edu.epu.eoffice_be.storage.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FileStorageProviderRegistry {

    private final Map<String, FileStorageProvider> providers = new ConcurrentHashMap<>();
    private final String activeProvider;

    public FileStorageProviderRegistry(List<FileStorageProvider> providerList,
                                       @Value("${file.storage.provider.active:telegram}") String activeProvider) {
        this.activeProvider = activeProvider;
        for (FileStorageProvider provider : providerList) {
            providers.put(provider.getName().toLowerCase(), provider);
        }
        log.info("Đã đăng ký các provider lưu trữ tệp: {} (đang dùng={})", providers.keySet(), activeProvider);
    }

    public FileStorageProvider getActiveProvider() {
        return Optional.ofNullable(providers.get(activeProvider.toLowerCase()))
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy FileStorageProvider nào đăng ký với tên '" + activeProvider + "'"));
    }

    public FileStorageProvider getProvider(String name) {
        return Optional.ofNullable(providers.get(name.toLowerCase()))
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy FileStorageProvider nào đăng ký với tên '" + name + "'"));
    }
}
