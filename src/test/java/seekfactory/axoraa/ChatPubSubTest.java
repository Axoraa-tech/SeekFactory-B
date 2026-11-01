package seekfactory.axoraa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import seekfactory.axoraa.services.ChatEventBus;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="seekfactory.localLoad", matches="true")
class ChatPubSubTest {
    @Test
    void bothInstancesReceiveCommittedEventsButNotRollbacks() throws Exception {
        try(var pg=EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses","127.0.0.1").start()) {
            var ds=pg.getPostgresDatabase();
            var jdbc=new JdbcTemplate(ds);
            CountDownLatch received=new CountDownLatch(2);
            AtomicInteger count=new AtomicInteger();
            org.springframework.context.ApplicationEventPublisher consumer=event -> {
                assertThat(event).isEqualTo(new ChatEventBus.Committed("c-1","m-1"));
                count.incrementAndGet(); received.countDown();
            };
            try(var first=new AutoBus(new ChatEventBus(jdbc,ds,consumer));
                var second=new AutoBus(new ChatEventBus(jdbc,ds,consumer))) {
                first.bus.start(); second.bus.start();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while((!first.bus.isListening() || !second.bus.isListening()) && System.nanoTime()<deadline) Thread.sleep(20);
                assertThat(first.bus.isListening() && second.bus.isListening()).isTrue();
                var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
                tx.executeWithoutResult(status -> { first.bus.publish("c-1","m-1"); status.setRollbackOnly(); });
                assertThat(received.await(300,TimeUnit.MILLISECONDS)).isFalse();
                assertThat(count.get()).isZero();
                tx.executeWithoutResult(status -> first.bus.publish("c-1","m-1"));
                assertThat(received.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(count.get()).isEqualTo(2);
                jdbc.execute("CREATE TABLE conversations (id varchar(64) PRIMARY KEY)");
                jdbc.execute("CREATE TABLE messages (id varchar(64) PRIMARY KEY, conversation_id varchar(64), sender_id varchar(64))");
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                        new org.springframework.core.io.ClassPathResource("db/migration/V25__message_idempotency.sql")).execute(ds);
                jdbc.update("INSERT INTO messages(id,conversation_id,sender_id,client_message_id) VALUES ('1','c','u','retry')");
                assertThatThrownBy(() -> jdbc.update("INSERT INTO messages(id,conversation_id,sender_id,client_message_id) VALUES ('2','c','u','retry')"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                jdbc.update("INSERT INTO messages(id,conversation_id,sender_id) VALUES ('3','c','u'),('4','c','u')");
            }
        }
    }
    private record AutoBus(ChatEventBus bus) implements AutoCloseable { public void close(){bus.close();} }
}
