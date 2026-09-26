package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;

public interface PlatformSettingsService {

    /** Current home-feed showcase layout; falls back to defaults if the row is missing. */
    FeedShowcaseSettings getFeedShowcase();

    FeedShowcaseSettings updateFeedShowcase(FeedShowcaseSettings settings, String adminId);
}
