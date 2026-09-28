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
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.Reels.ReelLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Reels.ReelSaveRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.ReelService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public List<FeedItemResponse> getFeed(FeedTab tab) {
        // Limit feed to top 50 items to prevent massive payloads and frontend overload
        // Approval is filtered in SQL and the manufacturer is joined in, so the feed
        // costs one query instead of one per reel and never over-fetches rows it
        // is about to discard.
        List<Reel> reels = reelRepository.findApprovedByFeedTab(tab, PageRequest.of(0, 50));

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
