package com.eventplatform.notification.provider;

public interface EmailSender {
    String name();
    ProviderReceipt send(ProviderMessage message);
}
