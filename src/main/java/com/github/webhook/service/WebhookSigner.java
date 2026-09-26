package com.github.webhook.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;


@Component
public class WebhookSigner {

    public String sign(String secret, String webhookId, long timestamp, String body) {
        try{
            String signPrompt = webhookId + "." + timestamp + "." + body;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(signPrompt.getBytes(StandardCharsets.UTF_8));

            return "v1," + Base64.getEncoder().encodeToString(raw);

        }
        catch (GeneralSecurityException ex){
            throw new IllegalStateException("Could not sign webhook", ex);
        }
    }
}