package com.eventplatform.notification.provider;

public interface SmsSender {
    String name();
    ProviderReceipt send(ProviderMessage message);
}
