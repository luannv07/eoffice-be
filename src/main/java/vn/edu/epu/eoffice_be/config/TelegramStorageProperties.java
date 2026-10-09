package vn.edu.epu.eoffice_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file.storage.provider.telegram")
@Getter
@Setter
public class TelegramStorageProperties {

    private String chatbotHttpApi;
    private String channelId;
}
