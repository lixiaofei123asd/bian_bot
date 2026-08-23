package com.lxf.bian_bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** DeepSeek API Key。 */
    private String apiKey;

    /** DeepSeek API 地址。 */
    private String baseUrl = "https://api.deepseek.com";

    /** 使用的模型。 */
    private String model = "deepseek-v4-pro";
}
