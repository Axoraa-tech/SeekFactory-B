package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.settings.ExchangeRatesResponse;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;

public interface PlatformSettingsService {

    /** Current home-feed showcase layout; falls back to defaults if the row is missing. */
    FeedShowcaseSettings getFeedShowcase();

    FeedShowcaseSettings updateFeedShowcase(FeedShowcaseSettings settings, String adminId);

    /** Display currency conversion rates (1 unit of base = rate units); empty rates if unset. */
    ExchangeRatesResponse getExchangeRates();
}
