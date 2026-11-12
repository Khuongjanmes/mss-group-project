package com.openevent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.notifications.Notification;
import com.openevent.notification.notifications.NotificationChannel;
import com.openevent.notification.notifications.NotificationType;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/** KB-5 bước 2 trên RabbitMQ, MongoDB và máy chủ SMTP thật (GreenMail); Identity, Event giả lập bằng Mockito. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.mail.host=localhost",
        "spring.mail.port=3025", "spring.mail.username=openevent@test.local", "spring.mail.password=secret",
        "MAIL_STARTTLS=false", "FRONTEND_URL=https://openevent.test",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1"})
class EventCancelledNotificationIT {

    static final MongoDBContainer DB = new MongoDBContainer("mongo:7");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    @RegisterExtension
    static final GreenMailExtension MAIL = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("openevent@test.local", "secret"));

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("MONGODB_URI", () -> DB.getConnectionString() + "/notification_db");
    }

    @MockitoBean
    IdentityClient identity;

    @MockitoBean
    EventClient events;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    MongoTemplate mongo;

    private final UUID eventId = UUID.randomUUID();
    private final UUID an = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();

    @BeforeEach
    void reset() {
        mongo.remove(new Query(), Notification.class);
        Instant start = Instant.parse("2026-12-20T12:00:00Z");
        when(events.getEvent(eventId)).thenReturn(new EventSummary(eventId, "Hội thảo AI", start,
                start.plusSeconds(3 * 3600), "Hội trường A", "1 Lê Duẩn", "Huế"));
        when(identity.getUsers(any())).thenReturn(List.of(new UserContact(an, "an@example.com", "Nguyễn An", null, "ACTIVE"),
                new UserContact(binh, "binh@example.com", "Trần Bình", null, "ACTIVE")));
    }

    @Test
    void everyBuyerIsEmailedOnceAndNotified() throws Exception {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKETS_CANCELLED, cancelled(an, binh, an));

        assertThat(MAIL.waitForIncomingEmail(15_000, 2)).isTrue();
        List<String> recipients = Arrays.stream(MAIL.getReceivedMessages())
                .map(EventCancelledNotificationIT::recipient).toList();
        assertThat(recipients).containsExactlyInAnyOrder("an@example.com", "binh@example.com");
        MimeMessage first = MAIL.getReceivedMessages()[0];
        assertThat(first.getSubject()).isEqualTo("Sự kiện đã bị hủy: Hội thảo AI");
        assertThat(html(first)).contains("Hội thảo AI", "ĐÃ HỦY", "Hoàn tiền", "https://openevent.test/orders");

        List<Notification> stored = awaitStored(2);
        assertThat(stored).extracting(Notification::getUserId).containsExactlyInAnyOrder(an, binh);
        assertThat(stored).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.EVENT_CANCELLED);
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        });
    }

    @Test
    void sameMessageDeliveredTwiceEmailsNobodyTwice() {
        TicketsCancelled message = cancelled(an, binh);

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKETS_CANCELLED, message);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKETS_CANCELLED, message);

        awaitStored(2);
        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(MAIL.getReceivedMessages()).hasSize(2);
            assertThat(mongo.findAll(Notification.class)).hasSize(2);
        });
    }

    private TicketsCancelled cancelled(UUID... owners) {
        return new TicketsCancelled(UUID.randomUUID(), Instant.now(), eventId, List.of(owners));
    }

    private List<Notification> awaitStored(int count) {
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(mongo.findAll(Notification.class)).hasSize(count));
        return mongo.findAll(Notification.class);
    }

    /** Phần text/html đã giải mã (quoted-printable) của email multipart. */
    private static String html(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = html(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
