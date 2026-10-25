package io.shortly.redirect.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.awaitility.Awaitility.await;

import io.shortly.redirect.AbstractIntegrationTest;
import io.shortly.redirect.ContractDatabase;
import io.shortly.redirect.producer.ClickProducer;
import io.shortly.redirect.service.FeedPoller;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class RedirectFlowTest extends AbstractIntegrationTest {

    @Autowired private Environment env;
    @Autowired private ContractDatabase db;
    @Autowired private FeedPoller poller;

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void setUp() {
        db.truncate();
        await().atMost(Duration.ofSeconds(5)).until(poller::isReady);
    }

    @Test
    void activeLinkRedirectsWithHeaders() throws Exception {
        db.upsertLink("flowAct01", "https://example.org/landing?x=1", "ACTIVE", 1);

        HttpResponse<String> response =
                send(
                        get("/flowAct01")
                                .header("Referer", "https://www.twitter.com/some/post")
                                .header("CloudFront-Viewer-Country", "CH"));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .contains("https://example.org/landing?x=1");
        assertThat(response.headers().firstValue("Cache-Control")).contains("private, no-store");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        List<String> clicks = clicksFor("flowAct01", Duration.ofSeconds(10));
        assertThat(clicks).hasSize(1);
        assertThat(clicks.getFirst())
                .contains("\"code\":\"flowAct01\"")
                .contains("\"outcome\":\"SERVED\"")
                .contains("\"country\":\"CH\"")
                .contains("\"ref\":\"www.twitter.com\"")
                .doesNotContain("some/post");
    }

    @Test
    void inactiveLinkIs410WithPage() throws Exception {
        db.upsertLink("flowIna01", "https://example.org/", "INACTIVE", 2);

        HttpResponse<String> response = send(get("/flowIna01"));

        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/html");
        assertThat(response.body()).contains("disabled");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'");
        assertThat(clicksFor("flowIna01", Duration.ofSeconds(10)))
                .singleElement(STRING)
                .contains("\"outcome\":\"BLOCKED\"");
    }

    @Test
    void deletedUnknownAndUnrecognisedAre404() throws Exception {
        db.upsertLink("flowDel01", "https://example.org/", "DELETED", 3);

        assertThat(send(get("/flowDel01")).statusCode()).isEqualTo(404);
        db.upsertLink("flowUnk01", "https://example.org/", "BLOCKED", 1);
        assertThat(send(get("/flowUnk01")).statusCode()).isEqualTo(404);
        HttpResponse<String> unknown = send(get("/zzzzzzzzz"));
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
        assertNoClick("flowDel01", "flowUnk01", "zzzzzzzzz");
    }

    @Test
    void headIsAnsweredButNotCounted() throws Exception {
        db.upsertLink("flowHead1", "https://example.org/", "ACTIVE", 1);

        HttpResponse<String> response =
                send(
                        HttpRequest.newBuilder(uri("/flowHead1"))
                                .method("HEAD", HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).contains("https://example.org/");
        assertThat(response.headers().firstValue("Cache-Control")).contains("private, no-store");
        assertNoClick("flowHead1");
    }

    @Test
    void uncachedCodeIs503WhileTheReplicaCannotBeAsked() throws Exception {
        db.jdbc().sql("ALTER VIEW link_snapshot RENAME TO link_snapshot_gone").update();
        try {
            HttpResponse<String> response = send(get("/flowDown1"));

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("Retry-After")).contains("1");
            assertThat(response.headers().firstValue("Cache-Control"))
                    .contains("private, no-store");
        } finally {
            db.jdbc().sql("ALTER VIEW link_snapshot_gone RENAME TO link_snapshot").update();
        }
        assertNoClick("flowDown1");
    }

    private static void assertNoClick(String... codes) {
        for (String code : codes) {
            assertThat(clicksFor(code, Duration.ofSeconds(1))).as(code).isEmpty();
        }
    }

    /**
     * The clicks published under {@code code}, read from the topic's start for up to {@code wait}.
     */
    private static List<String> clicksFor(String code, Duration wait) {
        Map<String, Object> config =
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions =
                    consumer.partitionsFor(ClickProducer.CLICKS).stream()
                            .map(p -> new TopicPartition(p.topic(), p.partition()))
                            .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            List<String> clicks = new ArrayList<>();
            long deadline = System.nanoTime() + wait.toNanos();
            while (clicks.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> click : consumer.poll(Duration.ofMillis(100))) {
                    if (code.equals(click.key())) {
                        clicks.add(click.value());
                    }
                }
            }
            return clicks;
        }
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + env.getProperty("local.server.port") + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
