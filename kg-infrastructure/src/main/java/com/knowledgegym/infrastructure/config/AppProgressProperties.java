package com.knowledgegym.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "app.progress")
public class AppProgressProperties {
    private ZoneId timezone = ZoneId.of("Asia/Ho_Chi_Minh");
    public ZoneId getTimezone() { return timezone; }
    public void setTimezone(ZoneId timezone) { this.timezone = timezone; }
}
