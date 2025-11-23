package org.github.webuild.homemind.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(HomeAssistantProperties.PREFIX)
public class HomeAssistantProperties {
    public static final String PREFIX = "home-assistant";

    private String url;
    private String token;
}
