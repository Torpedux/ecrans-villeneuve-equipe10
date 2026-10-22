package com.ecrans.vente.adapter.in.payvia;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class WebhookPayViaController {

    private final WebhookPayVia webhook;

    public WebhookPayViaController(WebhookPayVia webhook) {
        this.webhook = Objects.requireNonNull(webhook);
    }

    @PostMapping("/webhooks/payvia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recevoir(@RequestBody NotificationPayVia notification) {
        webhook.recevoir(notification);
    }
}
