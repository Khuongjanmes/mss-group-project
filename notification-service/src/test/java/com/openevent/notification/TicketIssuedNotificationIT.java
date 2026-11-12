package com.openevent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.notifications.Notification;
import com.openevent.notification.notifications.NotificationChannel;
import com.openevent.notification.notifications.NotificationType;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/** KB-4 bước 4 trên RabbitMQ, MongoDB và máy chủ SMTP thật (GreenMail); Identity, Event giả lập bằng Mockito. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.mail.host=localhost",
        "spring.mail.port=3025", "spring.mail.username=openevent@test.local", "spring.mail.password=secret",
        "MAIL_STARTTLS=false"})
class TicketIssuedNotificationIT {

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
    AmqpAdmin admin;

    @Autowired
    MongoTemplate mongo;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-12-20T12:00:00Z");

    @BeforeEach
    void reset() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Notification.class);
        admin.purgeQueue("notification-service.events.dlq");
        when(identity.getUser(ownerId))
                .thenReturn(new UserContact(ownerId, "an@example.com", "Nguyễn An", null, "ACTIVE"));
        when(events.getEvent(eventId)).thenReturn(new EventSummary(eventId, "Hội thảo AI", start,
                start.plusSeconds(3 * 3600), "Hội trường A", "1 Lê Duẩn", "Huế"));
    }

    @Test
    void issuedTicketsAreEmailedWithQrAndStored() throws Exception {
        TicketIssued message = ticketIssued(List.of("code-A", "code-B"));

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, message);

        assertThat(MAIL.waitForIncomingEmail(15_000, 1)).isTrue();
        MimeMessage email = MAIL.getReceivedMessages()[0];
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo("an@example.com");
        assertThat(email.getSubject()).isEqualTo("Vé của bạn: Hội thảo AI");
        List<Part> parts = leafParts(email);
        String html = (String) parts.stream().filter(this::isHtml).findFirst().orElseThrow().getContent();
        assertThat(html).contains("Nguyễn An", "code-A", "code-B", "Hội trường A, 1 Lê Duẩn, Huế",
                "cid:ticket-qr-0", "cid:ticket-qr-1");
        assertThat(parts.stream().filter(this::isPng)).hasSize(2);

        Notification stored = awaitStored(1).getFirst();
        assertThat(stored.getUserId()).isEqualTo(ownerId);
        assertThat(stored.getMessageId()).isEqualTo(message.messageId());
        assertThat(stored.getType()).isEqualTo(NotificationType.TICKET_ISSUED);
        assertThat(stored.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(stored.isRead()).isFalse();
    }

    @Test
    void sameMessageDeliveredTwiceSendsOneEmail() {
        TicketIssued message = ticketIssued(List.of("code-A"));

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, message);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, message);

        awaitStored(1);
        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(MAIL.getReceivedMessages()).hasSize(1);
            assertThat(mongo.findAll(Notification.class)).hasSize(1);
        });
    }

    @Test
    void identityDownMovesMessageToDeadLetterQueueWithoutEmail() {
        when(identity.getUser(any())).thenThrow(new IllegalStateException("identity-service down"));

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, ticketIssued(List.of("code-A")));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(admin.getQueueInfo("notification-service.events.dlq").getMessageCount()).isEqualTo(1));
        assertThat(MAIL.getReceivedMessages()).isEmpty();
        assertThat(mongo.findAll(Notification.class)).isEmpty();
    }

    @Test
    void messageAndUserAreUniqueInNotifications() {
        List<IndexInfo> indexes = mongo.indexOps(Notification.class).getIndexInfo();

        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getName()).isEqualTo("message_user");
            assertThat(index.isUnique()).isTrue();
        });
    }

    private TicketIssued ticketIssued(List<String> codes) {
        return new TicketIssued(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), ownerId, eventId, codes);
    }

    private List<Notification> awaitStored(int count) {
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(mongo.findAll(Notification.class)).hasSize(count));
        return mongo.findAll(Notification.class);
    }

    private boolean isHtml(Part part) {
        try {
            return part.isMimeType("text/html");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isPng(Part part) {
        try {
            return part.isMimeType("image/png");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Các phần lá của email multipart (HTML, ảnh nhúng). */
    private static List<Part> leafParts(Part part) throws Exception {
        List<Part> leaves = new ArrayList<>();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                leaves.addAll(leafParts(child));
            }
        } else {
            leaves.add(part);
        }
        return leaves;
    }
}
