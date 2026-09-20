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
import seekfactory.axoraa.entity.Reels.ReelLike;
import seekfactory.axoraa.entity.Reels.ReelSave;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.Reels.ReelLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Reels.ReelSaveRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.ReelService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ReelLikeRepository reelLikeRepository;
    private final ReelSaveRepository reelSaveRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<FeedItemResponse> getFeed(FeedTab tab) {
        List<Reel> reels = reelRepository.findByFeedTabOrderByCreatedAtDesc(tab);

        return reels.stream()
                .map(this::mapToFeedItem)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Map<String, Object> toggleLike(String reelId, String userId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));

        Optional<ReelLike> existing = reelLikeRepository.findByReelIdAndUserId(reelId, userId);
        boolean liked;
        if (existing.isPresent()) {
            reelLikeRepository.delete(existing.get());
            reel.setLikesCount(Math.max(0, reel.getLikesCount() - 1));
            liked = false;
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            ReelLike like = ReelLike.builder()
                    .reel(reel)
                    .user(user)
                    .build();
            reelLikeRepository.save(like);
            reel.setLikesCount(reel.getLikesCount() + 1);
            liked = true;
        }
        reelRepository.save(reel);
        return Map.of("liked", liked, "likesCount", reel.getLikesCount());
    }

    @Transactional
    @Override
    public Map<String, Object> toggleSave(String reelId, String userId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));

        Optional<ReelSave> existing = reelSaveRepository.findByReelIdAndUserId(reelId, userId);
        boolean saved;
        if (existing.isPresent()) {
            reelSaveRepository.delete(existing.get());
            reel.setSavesCount(Math.max(0, reel.getSavesCount() - 1));
            saved = false;
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            ReelSave save = ReelSave.builder()
                    .reel(reel)
                    .user(user)
                    .build();
            reelSaveRepository.save(save);
            reel.setSavesCount(reel.getSavesCount() + 1);
            saved = true;
        }
        reelRepository.save(reel);
        return Map.of("saved", saved, "savesCount", reel.getSavesCount());
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
