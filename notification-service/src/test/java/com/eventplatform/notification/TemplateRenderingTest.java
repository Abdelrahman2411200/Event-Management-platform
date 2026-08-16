package com.eventplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventplatform.notification.domain.NotificationChannel;
import com.eventplatform.notification.domain.NotificationType;
import com.eventplatform.notification.template.RenderedNotification;
import com.eventplatform.notification.template.TemplateCatalog;
import com.eventplatform.notification.template.TemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRenderingTest {
    private final TemplateRenderer renderer = new TemplateRenderer();
    private final TemplateCatalog catalog = new TemplateCatalog(renderer);

    @Test
    void rendersReusableLocaleReadyTemplateWithVariables() {
        RenderedNotification rendered = catalog.render(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "en-US",
                Map.of("amount", "25.00", "currency", "USD", "eventTitle", "Phase 6 Conference"));

        assertThat(rendered.subject()).isEqualTo("Payment confirmed");
        assertThat(rendered.body()).contains("25.00 USD").contains("Phase 6 Conference");
    }

    @Test
    void rejectsMissingVariablesInsteadOfSendingBrokenContent() {
        assertThatThrownBy(() -> renderer.render("Hello {{name}}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
