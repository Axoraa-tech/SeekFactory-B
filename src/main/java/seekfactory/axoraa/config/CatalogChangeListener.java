package seekfactory.axoraa.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;

import java.util.Set;

/**
 * Tells the catalogue caches (factories, category products, search, feed) that what they show
 * changed: a product, seek, factory or category was saved or removed, by sellers, admins or any
 * other path. Fires after the commit, so a rebuild reads the new data. Counter-only updates
 * (likes, views, followers...) are ignored: they change constantly and don't affect what is listed.
 */
@Component
@RequiredArgsConstructor
public class CatalogChangeListener
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    /** Published after a committed catalogue change; listeners mark their caches stale. */
    public record CatalogChangedEvent() {}

    private static final Set<Class<?>> WATCHED = Set.of(Product.class, Reel.class, Manufacturer.class, Category.class);
    private static final Set<String> COUNTERS = Set.of(
            "viewsCount", "likesCount", "commentsCount", "sharesCount", "savesCount", "followerCount", "listingCount");

    private final EntityManagerFactory entityManagerFactory;
    private final ApplicationEventPublisher publisher;

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (watched(event.getEntity())) changed();
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (watched(event.getEntity()) && !onlyCounters(event)) changed();
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (watched(event.getEntity())) changed();
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return WATCHED.contains(persister.getMappedClass());
    }

    private static boolean watched(Object entity) {
        return entity != null && WATCHED.contains(entity.getClass());
    }

    private static boolean onlyCounters(PostUpdateEvent event) {
        int[] dirty = event.getDirtyProperties();
        if (dirty == null || dirty.length == 0) return false;
        String[] names = event.getPersister().getPropertyNames();
        for (int index : dirty) {
            if (!COUNTERS.contains(names[index])) return false;
        }
        return true;
    }

    private void changed() {
        publisher.publishEvent(new CatalogChangedEvent());
    }
}
