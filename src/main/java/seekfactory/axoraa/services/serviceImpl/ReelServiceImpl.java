package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.services.services.ReelService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the video reels feed — the core discovery mechanism of SeekFactory.
 *
 * Returns FeedItemResponse which bundles each reel with its manufacturer info
 * and an optional primary product slug for the product overlay card.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReelServiceImpl implements ReelService {

    private final ReelRepository reelRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<FeedItemResponse> getFeed(FeedTab tab) {
        List<Reel> reels = reelRepository.findByFeedTabOrderByCreatedAtDesc(tab);

        return reels.stream()
                .map(this::mapToFeedItem)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private FeedItemResponse mapToFeedItem(Reel reel) {
        Manufacturer manufacturer = reel.getManufacturer();

        // Map reel to response
        ReelResponse reelResponse = ReelResponse.builder()
                .id(reel.getId())
                .manufacturerId(manufacturer.getId())
                .title(reel.getTitle())
                .description(reel.getDescription())
                .hashtags(new ArrayList<>(reel.getHashtags()))
                .posterUrl(reel.getPosterUrl())
                .videoUrl(reel.getVideoUrl())
                .durationSec(reel.getDurationSec())
                .startSec(reel.getStartSec())
                .views(reel.getViewsCount())
                .likes(reel.getLikesCount())
                .comments(reel.getCommentsCount())
                .shares(reel.getSharesCount())
                .saves(reel.getSavesCount())
                .tab(reel.getFeedTab().name().toLowerCase().replace("_", "-"))
                .productIds(reel.getProducts().stream()
                        .map(p -> p.getId())
                        .collect(Collectors.toList()))
                .build();

        // Map manufacturer to response
        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        // Determine primary product slug (first featured product, if any)
        String primaryProductSlug = reel.getProducts().stream()
                .findFirst()
                .map(p -> p.getSlug())
                .orElse(null);

        return FeedItemResponse.builder()
                .reel(reelResponse)
                .manufacturer(mfgResponse)
                .primaryProductSlug(primaryProductSlug)
                .build();
    }
}