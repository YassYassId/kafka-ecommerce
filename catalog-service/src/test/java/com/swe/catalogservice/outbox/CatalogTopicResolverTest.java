package com.swe.catalogservice.outbox;

import com.swe.catalogservice.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTopicResolverTest {

    private final CatalogTopicResolver topicResolver = new CatalogTopicResolver();

    @ParameterizedTest(name = "event type \"{0}\" should resolve to topic \"{1}\"")
    @CsvSource({
            "ProductCreated, product.created",
            "ProductUpdated, product.updated",
            "PriceChanged, product.price-changed",
            "ProductRetired, product.retired"
    })
    @DisplayName("should resolve known event types to corresponding topics")
    void resolve_WhenEventTypeIsKnown_ShouldReturnCorrectTopic(String eventType, String expectedTopic) {
        String topic = topicResolver.resolve(eventType);
        assertThat(topic).isEqualTo(expectedTopic);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when event type is unknown")
    void resolve_WhenEventTypeIsUnknown_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> topicResolver.resolve("UnknownEventType"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported Catalog event type: UnknownEventType");
    }
}
