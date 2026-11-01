package seekfactory.axoraa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import seekfactory.axoraa.entity.*;
import seekfactory.axoraa.entity.Messages.Conversation;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.*;
import seekfactory.axoraa.repository.*;
import seekfactory.axoraa.repository.Messages.*;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in real HTTP/PostgreSQL test. Never reads application.yaml, .env, or environment credentials. */
@EnabledIfSystemProperty(named = "seekfactory.localLoad", matches = "true")
class LocalConcurrencyTest {
    @Test
    void isolatedHttpRampAndConcurrencyChecks(@TempDir Path temp) throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(0)
                .setServerConfig("listen_addresses", "127.0.0.1")
                .setServerConfig("max_connections", "60").start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            assertThat(url).startsWith("jdbc:postgresql://localhost:");
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            SpringApplication app = new SpringApplication(AxoraaApplication.class);
            app.setEnvironment(environment);
            try (var context = app.run("--spring.config.location=classpath:/local-load.yaml",
                    "--spring.datasource.url=" + url, "--spring.datasource.username=postgres",
                    "--spring.datasource.password=postgres", "--app.media.storage-dir=" + temp.resolve("media"))) {
                String base = "http://127.0.0.1:" + ((WebServerApplicationContext)context).getWebServer().getPort();
                var users = context.getBean(UserRepository.class);
                User supplier = users.save(User.builder().name("Synthetic supplier").email("supplier@load.invalid")
                        .role(UserRole.ROLE_SUPPLIER).authProvider(AuthProvider.LOCAL).build());
                Manufacturer factory = context.getBean(ManufacturerRepository.class).save(Manufacturer.builder()
                        .user(supplier).name("Synthetic factory").slug("synthetic-factory").country("Test")
                        .location("Local only").verified(true).build());
                Reel reel = context.getBean(ReelRepository.class).save(Reel.builder().manufacturer(factory)
                        .title("Synthetic reel").posterUrl("/local-test-poster.png").build());
                List<User> buyers = new ArrayList<>();
                for (int i=0; i<500; i++) buyers.add(User.builder().name("Synthetic " + i)
                        .email("buyer" + i + "@load.invalid").role(UserRole.ROLE_BUYER).authProvider(AuthProvider.LOCAL).build());
                buyers = users.saveAll(buyers);
                var conversations = context.getBean(ConversationRepository.class);
                Conversation shared = conversations.save(Conversation.builder().buyer(buyers.getFirst()).manufacturer(factory).build());
                List<Conversation> threads = new ArrayList<>();
                threads.add(shared);
                for(int i=1;i<500;i++) threads.add(Conversation.builder().buyer(buyers.get(i)).manufacturer(factory).build());
                threads = conversations.saveAll(threads);
                var jwt = context.getBean(JwtTokenProvider.class);
                List<String> tokens = buyers.stream().map(u -> jwt.generateAccessToken(u.getId(), u.getEmail(), "ROLE_BUYER")).toList();
                String buyerToken = tokens.getFirst();
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                StringBuilder report = new StringBuilder("Local-only HTTP load; PostgreSQL 14; synthetic data; backend and load generator share this laptop.\n");
                report.append("available_processors=").append(Runtime.getRuntime().availableProcessors())
                        .append(" max_heap_mib=").append(Runtime.getRuntime().maxMemory()/1048576).append('\n');
                assertThat(send(client, base + "/api/v1/feed?size=10", null, null).statusCode()).isEqualTo(200);

                // Same conversation, same client ID: exactly one stored send across parallel retries.
                String body = "{\"messageText\":\"retry-safe\",\"clientMessageId\":\"same-send\"}";
                List<CompletableFuture<HttpResponse<String>>> retries = new ArrayList<>();
                for (int i=0; i<20; i++) retries.add(client.sendAsync(request(base + "/api/v1/conversations/" + shared.getId() + "/messages", buyerToken, body), HttpResponse.BodyHandlers.ofString()));
                for(var retry : retries) assertThat(retry.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
                assertThat(context.getBean(MessageRepository.class).findByConversationIdOrderByCreatedAtAsc(shared.getId())).hasSize(1);

                // Different IDs in the same hot conversation must not lose unread increments.
                retries.clear();
                for(int i=0;i<100;i++) retries.add(client.sendAsync(request(base+"/api/v1/conversations/"+shared.getId()+"/messages",
                        buyerToken,"{\"messageText\":\"parallel\",\"clientMessageId\":\"parallel-"+i+"\"}"), HttpResponse.BodyHandlers.ofString()));
                for(var retry:retries) assertThat(retry.get(30,TimeUnit.SECONDS).statusCode()).isEqualTo(201);
                assertThat(conversations.findById(shared.getId()).orElseThrow().getUnreadCountSupplier()).isEqualTo(101);
                assertThat(context.getBean(MessageRepository.class).findByConversationIdOrderByCreatedAtAsc(shared.getId())).hasSize(101);
                report.append("20 duplicate sends => 1 message; 100 distinct concurrent sends => 101 total messages/unread.\n");
                var chat=context.getBean(seekfactory.axoraa.services.services.ConversationService.class);
                var firstPage=chat.syncMessages(shared.getId(),buyers.getFirst().getId(),0);
                assertThat(firstPage.messages()).hasSize(100);
                assertThat(firstPage.hasMore()).isTrue();
                var secondPage=chat.syncMessages(shared.getId(),buyers.getFirst().getId(),firstPage.cursor());
                assertThat(secondPage.messages()).hasSize(1);
                String changedId=firstPage.messages().getFirst().getId();
                chat.editMessage(shared.getId(),changedId,buyers.getFirst().getId(),"edited");
                chat.deleteMessage(shared.getId(),changedId,buyers.getFirst().getId());
                var recovered=chat.syncMessages(shared.getId(),buyers.getFirst().getId(),secondPage.cursor());
                assertThat(recovered.messages()).hasSize(1);
                assertThat(recovered.messages().getFirst().isDeleted()).isTrue();
                assertThat(recovered.cursor()).isGreaterThan(secondPage.cursor());
                report.append("Cursor pagination and edit/delete recovery passed.\n");

                for (int concurrency : new int[]{25,100,250,500}) {
                    List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
                    AtomicInteger errors = new AtomicInteger();
                    long started = System.nanoTime();
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> tasks = new ArrayList<>();
                        for(int i=0; i<concurrency; i++) {
                            final int index=i;
                            tasks.add(executor.submit(() -> {
                                for(int step=0; step<5; step++) {
                                    long before=System.nanoTime();
                                    try {
                                        // 80% authenticated reads, 20% distinct-user reactions on one hot reel.
                                        String endpoint = step==4 ? "/api/v1/feed/"+reel.getId()+"/like" : "/api/v1/feed?size=10";
                                        var response=send(client, base+endpoint, tokens.get(index), step==4 ? "" : null);
                                        if(response.statusCode()!=200) errors.incrementAndGet();
                                    } catch(Exception failure) { errors.incrementAndGet(); }
                                    latencies.add((System.nanoTime()-before)/1_000_000);
                                }
                            }));
                        }
                        for(var task:tasks) task.get(90,TimeUnit.SECONDS);
                    }
                    Collections.sort(latencies);
                    long elapsed=(System.nanoTime()-started)/1_000_000;
                    long p95=latencies.get((int)Math.floor((latencies.size()-1)*.95));
                    long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                    report.append("concurrency=").append(concurrency).append(" requests=").append(latencies.size())
                            .append(" errors=").append(errors).append(" p95_ms=").append(p95)
                            .append(" elapsed_ms=").append(elapsed).append(" heap_used_mib=").append(heap/1048576).append('\n');
                    Files.writeString(Path.of("target/local-load-report.txt"),report);
                    assertThat(errors.get()).as("Stop on any errors").isZero();
                    assertThat(p95).as("Stop on p95 over 10 seconds").isLessThan(10000);
                }
                // Earlier stages toggle the same users: 25..99 and 250..499 remain liked.
                assertThat(context.getBean(ReelRepository.class).findById(reel.getId()).orElseThrow().getLikesCount())
                        .isEqualTo(325);

                // Sustained mixed clients: 75% feed, 15% sync, 5% chat writes, 5% small image uploads.
                byte[] png;
                try(var bytes=new java.io.ByteArrayOutputStream()) {
                    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
                    png=bytes.toByteArray();
                }
                byte[] prefix=("--local-boundary\r\nContent-Disposition: form-data; name=\"kind\"\r\n\r\nimage\r\n"
                        +"--local-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] suffix="\r\n--local-boundary--\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] multipart=new byte[prefix.length+png.length+suffix.length];
                System.arraycopy(prefix,0,multipart,0,prefix.length); System.arraycopy(png,0,multipart,prefix.length,png.length);
                System.arraycopy(suffix,0,multipart,prefix.length+png.length,suffix.length);
                final List<Conversation> allThreads=threads;
                List<Long> mixedLatencies=Collections.synchronizedList(new ArrayList<>());
                AtomicInteger mixedErrors=new AtomicInteger();
                long mixedStart=System.nanoTime(), until=mixedStart+TimeUnit.SECONDS.toNanos(20);
                try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                    List<Future<?>> tasks=new ArrayList<>();
                    for(int i=0;i<500;i++) {
                        final int index=i;
                        tasks.add(executor.submit(() -> {
                            int iteration=0;
                            while(System.nanoTime()<until && mixedErrors.get()==0) {
                                long before=System.nanoTime();
                                try {
                                    HttpRequest req;
                                    int expected=200;
                                    String path="/api/v1/conversations/"+allThreads.get(index).getId()+"/messages";
                                    if(index%20==0) {
                                        req=request(base+path,tokens.get(index),"{\"messageText\":\"mixed\",\"clientMessageId\":\"mixed-"+iteration+"\"}"); expected=201;
                                    } else if(index%20==1) {
                                        req=HttpRequest.newBuilder(URI.create(base+"/api/v1/media")).timeout(Duration.ofSeconds(20))
                                                .header("Authorization","Bearer "+tokens.get(index))
                                                .header("Content-Type","multipart/form-data; boundary=local-boundary")
                                                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart)).build(); expected=201;
                                    } else if(index%20<5) req=request(base+path+"/sync?after=0",tokens.get(index),null);
                                    else req=request(base+"/api/v1/feed?size=10",tokens.get(index),null);
                                    if(client.send(req,HttpResponse.BodyHandlers.discarding()).statusCode()!=expected) mixedErrors.incrementAndGet();
                                } catch(Exception failure) { mixedErrors.incrementAndGet(); }
                                long latency=(System.nanoTime()-before)/1_000_000;
                                mixedLatencies.add(latency);
                                if(latency>10000) mixedErrors.incrementAndGet();
                                iteration++;
                                try { Thread.sleep(1000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
                            }
                        }));
                    }
                    for(var task:tasks) task.get(60,TimeUnit.SECONDS);
                }
                Collections.sort(mixedLatencies);
                report.append("mixed_500 duration_ms=").append((System.nanoTime()-mixedStart)/1_000_000)
                        .append(" requests=").append(mixedLatencies.size()).append(" errors=").append(mixedErrors.get())
                        .append(" p95_ms=").append(mixedLatencies.get((int)((mixedLatencies.size()-1)*.95))).append('\n');
                Files.writeString(Path.of("target/local-load-report.txt"),report);
                assertThat(mixedErrors.get()).as("Sustained mixed load must stop on errors or >10s latency").isZero();
            }
        }
    }

    private static HttpRequest request(String url,String token,String body) {
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        return builder.build();
    }
    private static HttpResponse<String> send(HttpClient client,String url,String token,String body) throws Exception {
        return client.send(request(url,token,body),HttpResponse.BodyHandlers.ofString());
    }
}
