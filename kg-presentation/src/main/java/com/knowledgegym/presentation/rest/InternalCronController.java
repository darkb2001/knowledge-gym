package com.knowledgegym.presentation.rest;

import com.knowledgegym.blog.application.CollectItemsUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, token-protected triggers for an external scheduler (Lambda/LXC cron). */
@RestController
@RequestMapping("/internal")
public class InternalCronController {
    private final CollectItemsUseCase collector;
    private final String cronToken;

    public InternalCronController(CollectItemsUseCase collector,
                                  @Value("${app.cron.token:}") String cronToken) {
        this.collector = collector;
        this.cronToken = cronToken;
    }

    @PostMapping("/collect")
    public ResponseEntity<?> collect(@RequestHeader(value = "X-Cron-Token", required = false) String token) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(collector.execute());
    }

    @PostMapping("/backup")
    public ResponseEntity<Void> backup(@RequestHeader(value = "X-Cron-Token", required = false) String token) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        // The backup command must be wired to scripts/backup-db.sh before production use.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/challenge/daily")
    public ResponseEntity<Void> dailyChallenge(@RequestHeader(value = "X-Cron-Token", required = false) String token) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private boolean authorized(String supplied) {
        return cronToken != null && !cronToken.isBlank()
                && supplied != null
                && MessageDigest.isEqual(
                    cronToken.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8));
    }
}
