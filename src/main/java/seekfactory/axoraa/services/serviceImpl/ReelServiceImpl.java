package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.Reels.ReelLike;
import seekfactory.axoraa.entity.Reels.ReelSave;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.Reels.ReelLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Reels.ReelSaveRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.ReelService;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages the video reels feed — the core discovery mechanism of SeekFactory.
 *
 * Returns FeedItemResponse which bundles each reel with its manufacturer info,
 * its active products and, for signed-in viewers, their liked / saved / following state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReelServiceImpl implements ReelService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReelRepository reelRepository;
    private final ReelLikeRepository reelLikeRepository;
    private final ReelSaveRepository reelSaveRepository;
    private final UserRepository userRepository;
    private final CatalogMapper catalogMapper;

    @Override
    public List<FeedItemResponse> getFeed(FeedTab tab, String viewerId, int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize > 0 ? safeSize : MAX_PAGE_SIZE);
        // Approval is filtered in SQL and the manufacturer is joined in, so the feed
        // costs one query instead of one lazy load per reel.
        List<Reel> reels = tab == FeedTab.FOLLOWING && viewerId != null
                ? reelRepository.findFollowingFeed(viewerId, pageable)
                : reelRepository.findVisibleFeed(pageable);
        return catalogMapper.toFeedItems(reels, viewerId);
    }

    @Transactional
    @Override
    public Map<String, Object> toggleLike(String reelId, String userId, String reaction) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        String chosen = normaliseReaction(reaction);

        Optional<ReelLike> existing = reelLikeRepository.findByReelIdAndUserId(reelId, userId);
        String current;

        if (existing.isPresent() && existing.get().getReaction().equals(chosen)) {
            // Same reaction again: take it back
            reelLikeRepository.delete(existing.get());
            reel.setLikesCount(Math.max(0, reel.getLikesCount() - 1));
            current = null;
        } else if (existing.isPresent()) {
            // A different reaction replaces the old one; the total count is unchanged
            existing.get().setReaction(chosen);
            reelLikeRepository.save(existing.get());
            current = chosen;
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            ReelLike like = ReelLike.builder()
                    .reel(reel)
                    .user(user)
                    .reaction(chosen)
                    .build();
            reelLikeRepository.save(like);
            reel.setLikesCount(reel.getLikesCount() + 1);
            current = chosen;
        }

        reelRepository.save(reel);
        Map<String, Object> result = new HashMap<>();
        result.put("liked", current != null);
        result.put("likesCount", reel.getLikesCount());
        result.put("reaction", current);
        return result;
    }

    private static final Set<String> REACTIONS = Set.of("LIKE", "LOVE", "STAR");

    /** LIKE when absent (older clients); anything else must be one of the known reactions. */
    private static String normaliseReaction(String reaction) {
        if (reaction == null || reaction.isBlank()) return "LIKE";
        String value = reaction.trim().toUpperCase(Locale.ROOT);
        if (!REACTIONS.contains(value)) throw new BadRequestException("Unknown reaction: " + reaction);
        return value;
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

    @Transactional
    @Override
    public Map<String, Object> recordShare(String reelId) {
        if (reelRepository.incrementSharesCount(reelId) == 0) {
            throw new ResourceNotFoundException("Reel", "id", reelId);
        }
        int shares = reelRepository.findById(reelId).map(Reel::getSharesCount).orElse(0);
        return Map.of("sharesCount", shares);
    }

    @Override
    public List<FeedItemResponse> listSaved(String userId) {
        List<Reel> reels = reelSaveRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReelSave::getReel)
                .filter(r -> Boolean.TRUE.equals(r.getManufacturer().getVerified()) && Boolean.TRUE.equals(r.getListed()))
                .toList();
        return catalogMapper.toFeedItems(reels, userId);
    }
}
