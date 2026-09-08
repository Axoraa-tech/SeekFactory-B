# SeekFactory Backend — Part 3: Service Implementations

> All 9 remaining `ServiceImpl` classes. AuthServiceImpl was provided in Part 2.

---

## 1. `UserServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.response.user.UserResponse;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.UserService;

/**
 * Manages user profile operations.
 * All profile updates are performed on the currently authenticated user only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        User user = findUserOrThrow(userId);
        return mapToResponse(user);
    }

    @Override
    public UserResponse updateProfile(String userId, UserUpdateRequest request) {
        User user = findUserOrThrow(userId);

        // Update only non-null fields (partial update pattern)
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getCompanyName() != null) {
            user.setCompanyName(request.getCompanyName());
        }
        if (request.getIndustry() != null) {
            user.setIndustry(request.getIndustry());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User saved = userRepository.save(user);
        log.info("User profile updated: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public void deactivateUser(String userId) {
        User user = findUserOrThrow(userId);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", userId);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = modelMapper.map(user, UserResponse.class);
        // Map enum to frontend-friendly string
        response.setRole(switch (user.getRole()) {
            case ROLE_BUYER -> "Buyer";
            case ROLE_SUPPLIER -> "Supplier";
            case ROLE_ADMIN -> "Admin";
        });
        return response;
    }
}
```

---

## 2. `ManufacturerServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.response.product.ProductResponse;
import seekfactory.axoraa.dto.response.reel.ReelResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reel;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.ManufacturerService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles manufacturer profile retrieval and listing.
 *
 * The getBySlug method returns a composite response containing:
 * - Manufacturer profile data
 * - All active products by this manufacturer
 * - All video reels by this manufacturer
 *
 * This matches the frontend ManufacturerDetail type in contracts.ts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final seekfactory.axoraa.repository.Reels.ReelRepository reelRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ManufacturerResponse> listVerified(int limit) {
        List<Manufacturer> verified = manufacturerRepository.findByVerifiedTrueOrderByFollowerCountDesc();

        // Apply limit
        if (limit > 0 && verified.size() > limit) {
            verified = verified.subList(0, limit);
        }

        return verified.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ManufacturerDetailResponse getBySlug(String slug) {
        Manufacturer manufacturer = manufacturerRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "slug", slug));

        // Fetch related products and reels
        List<Product> products = productRepository
                .findByManufacturerIdAndIsActiveTrue(manufacturer.getId());
        List<Reel> reels = reelRepository
                .findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId());

        return ManufacturerDetailResponse.builder()
                .manufacturer(mapToResponse(manufacturer))
                .products(products.stream()
                        .map(p -> modelMapper.map(p, ProductResponse.class))
                        .collect(Collectors.toList()))
                .reels(reels.stream()
                        .map(this::mapReelToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<ManufacturerResponse> listAll() {
        return manufacturerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ManufacturerResponse mapToResponse(Manufacturer m) {
        ManufacturerResponse response = modelMapper.map(m, ManufacturerResponse.class);
        // Map ElementCollection and ManyToMany to simple lists
        response.setExportCountries(new ArrayList<>(m.getExportCountries()));
        response.setCategoryIds(m.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));
        return response;
    }

    private ReelResponse mapReelToResponse(Reel reel) {
        ReelResponse response = modelMapper.map(reel, ReelResponse.class);
        response.setViews(reel.getViewsCount());
        response.setLikes(reel.getLikesCount());
        response.setComments(reel.getCommentsCount());
        response.setShares(reel.getSharesCount());
        response.setSaves(reel.getSavesCount());
        response.setTab(reel.getFeedTab().name().toLowerCase().replace("_", "-"));
        response.setHashtags(new ArrayList<>(reel.getHashtags()));
        response.setProductIds(reel.getProducts().stream()
                .map(p -> p.getId())
                .collect(Collectors.toList()));
        return response;
    }
}
```

---

## 3. `ProductServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.response.product.ProductResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.ProductService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles product listing, detail retrieval, and category-based filtering.
 *
 * The getBySlug method returns:
 * - Product details
 * - Manufacturer info (so the frontend can show the factory badge)
 * - Related products from the same manufacturer (excluding current product)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ProductResponse> listTrending(int limit) {
        int effectiveLimit = limit > 0 ? limit : 20;
        List<Product> trending = productRepository.findTrending(PageRequest.of(0, effectiveLimit));

        return trending.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDetailResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));

        Manufacturer manufacturer = product.getManufacturer();

        // Get related products from the same manufacturer, excluding current
        List<Product> related = productRepository
                .findByManufacturerIdAndIdNot(manufacturer.getId(), product.getId());

        // Limit related to 6
        if (related.size() > 6) {
            related = related.subList(0, 6);
        }

        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        return ProductDetailResponse.builder()
                .product(mapToResponse(product))
                .manufacturer(mfgResponse)
                .related(related.stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<ProductResponse> listByCategory(String categoryId) {
        return productRepository.findByCategoryIdAndIsActiveTrue(categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setManufacturerId(product.getManufacturer().getId());
        response.setCategoryId(product.getCategory().getId());
        return response;
    }
}
```

---

## 4. `ReelServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.response.reel.FeedItemResponse;
import seekfactory.axoraa.dto.response.reel.ReelResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Reel;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.services.ReelService;

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
```

---

## 5. `CategoryServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.category.CategoryResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.services.CategoryService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the hierarchical machinery taxonomy.
 *
 * Categories are organized in a tree:
 * - Root categories (parentId = null): "Agriculture", "Machine Tools", "Energy", etc.
 * - Subcategories: "Harvesters" under "Agriculture", "CNC Lathes" under "Machine Tools"
 *
 * The listChildren method accepts either a parent ID or a parent slug,
 * matching both frontends' CategoryRepository.listChildren(parentIdOrSlug).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> listRoots() {
        return categoryRepository.findByParentIsNullOrderByNameAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> listChildren(String parentIdOrSlug) {
        // Try by ID first, then by slug
        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(parentIdOrSlug);

        if (children.isEmpty()) {
            // Might be a slug — try finding parent by slug, then get its children
            Category parent = categoryRepository.findBySlug(parentIdOrSlug)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category", "id or slug", parentIdOrSlug));
            children = categoryRepository.findByParentIdOrderByNameAsc(parent.getId());
        }

        return children.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return mapToResponse(category);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private CategoryResponse mapToResponse(Category category) {
        CategoryResponse response = modelMapper.map(category, CategoryResponse.class);
        // Map parent relationship to simple parentId string
        response.setParentId(category.getParent() != null ? category.getParent().getId() : null);
        return response;
    }
}
```

---

## 6. `CommentServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.response.comment.CommentReplyResponse;
import seekfactory.axoraa.dto.response.comment.CommentResponse;
import seekfactory.axoraa.entity.Comment;
import seekfactory.axoraa.entity.Reel;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CommentRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.CommentService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages reel comments and threaded replies.
 *
 * Comment author metadata is denormalized at write time (authorName, authorAvatarUrl,
 * authorCompany, authorCountry) to avoid expensive joins during feed rendering.
 * This is a common pattern in high-read, low-write comment systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ReelRepository reelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listByReelId(String reelId) {
        // Fetch only top-level comments (parent is null)
        // Replies are loaded via the @OneToMany relationship on Comment entity
        List<Comment> comments = commentRepository
                .findByReelIdAndParentIsNullOrderByCreatedAtDesc(reelId);

        return comments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CommentResponse addComment(String reelId, String userId, CommentCreateRequest request) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Comment comment = Comment.builder()
                .reel(reel)
                .user(user)
                .authorName(user.getName())
                .authorAvatarUrl(user.getAvatarUrl())
                .authorCompany(user.getCompanyName())
                .authorCountry(user.getCountry())
                .isVerified(false)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);

        // Increment reel comment count
        reel.setCommentsCount(reel.getCommentsCount() + 1);
        reelRepository.save(reel);

        log.info("Comment added to reel {} by user {}", reelId, userId);
        return mapToResponse(saved);
    }

    @Override
    public CommentReplyResponse addReply(String commentId, String userId, ReplyCreateRequest request) {
        Comment parentComment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Comment reply = Comment.builder()
                .reel(parentComment.getReel())
                .parent(parentComment)
                .user(user)
                .authorName(user.getName())
                .authorAvatarUrl(user.getAvatarUrl())
                .authorCompany(user.getCompanyName())
                .authorCountry(user.getCountry())
                .isVerified(false)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(reply);
        log.info("Reply added to comment {} by user {}", commentId, userId);

        return mapToReplyResponse(saved);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .reelId(comment.getReel().getId())
                .authorName(comment.getAuthorName())
                .authorAvatarUrl(comment.getAuthorAvatarUrl())
                .authorCompany(comment.getAuthorCompany())
                .authorCountry(comment.getAuthorCountry())
                .isVerified(comment.getIsVerified())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().toString())
                .likes(comment.getLikesCount())
                .replies(comment.getReplies().stream()
                        .map(this::mapToReplyResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private CommentReplyResponse mapToReplyResponse(Comment reply) {
        return CommentReplyResponse.builder()
                .id(reply.getId())
                .authorName(reply.getAuthorName())
                .authorAvatarUrl(reply.getAuthorAvatarUrl())
                .authorCompany(reply.getAuthorCompany())
                .authorCountry(reply.getAuthorCountry())
                .isVerified(reply.getIsVerified())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt().toString())
                .likes(reply.getLikesCount())
                .build();
    }
}
```

---

## 7. `RfqServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Rfq;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.Incoterm;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.RfqService;
import seekfactory.axoraa.utils.IdGenerator;

import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages Request for Quotation lifecycle.
 *
 * When a buyer submits an RFQ, the system:
 * 1. Generates a unique reference number (e.g., "RFQ-2026-000142")
 * 2. Associates it with the buyer's user account
 * 3. Sets initial status to SUBMITTED
 * 4. Returns the created RFQ with its tracking reference
 *
 * The reference number format: RFQ-{YEAR}-{6-DIGIT-SEQUENCE}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RfqServiceImpl implements RfqService {

    private final RfqRepository rfqRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public RfqResponse submit(String userId, RfqCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Generate unique reference number
        int currentYear = Year.now().getValue();
        String yearPrefix = "RFQ-" + currentYear + "-%";
        int nextSequence;
        try {
            nextSequence = rfqRepository.findMaxSequenceForYear(yearPrefix) + 1;
        } catch (Exception e) {
            nextSequence = 1;
        }
        String referenceNumber = IdGenerator.generateRfqReference(currentYear, nextSequence);

        // Resolve optional category
        Category category = null;
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        // Parse currency and incoterm with defaults
        Currency currency = parseCurrency(request.getCurrency());
        Incoterm incoterm = parseIncoterm(request.getIncoterm());

        Rfq rfq = Rfq.builder()
                .referenceNumber(referenceNumber)
                .user(user)
                .productName(request.getProductName())
                .category(category)
                .quantity(request.getQuantity())
                .unit(request.getUnit() != null ? request.getUnit() : "Pieces")
                .targetPrice(request.getTargetPrice())
                .currency(currency)
                .incoterm(incoterm)
                .companyName(request.getCompanyName())
                .details(request.getDetails())
                .attachmentName(request.getAttachmentName())
                .attachmentSize(request.getAttachmentSize())
                .attachmentUrl(request.getAttachmentUrl())
                .status(RfqStatus.SUBMITTED)
                .build();

        Rfq saved = rfqRepository.save(rfq);
        log.info("RFQ submitted: {} by user {}", referenceNumber, userId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfqResponse> listByUser(String userId) {
        return rfqRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private RfqResponse mapToResponse(Rfq rfq) {
        return RfqResponse.builder()
                .id(rfq.getId())
                .referenceNumber(rfq.getReferenceNumber())
                .productName(rfq.getProductName())
                .categoryId(rfq.getCategory() != null ? rfq.getCategory().getId() : null)
                .quantity(rfq.getQuantity())
                .unit(rfq.getUnit())
                .targetPrice(rfq.getTargetPrice())
                .currency(rfq.getCurrency().name())
                .incoterm(rfq.getIncoterm().name())
                .companyName(rfq.getCompanyName())
                .details(rfq.getDetails())
                .attachmentName(rfq.getAttachmentName())
                .status(rfq.getStatus().name())
                .createdAt(rfq.getCreatedAt().toString())
                .build();
    }

    private Currency parseCurrency(String value) {
        if (value == null || value.isBlank()) return Currency.INR;
        try {
            return Currency.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Currency.INR;
        }
    }

    private Incoterm parseIncoterm(String value) {
        if (value == null || value.isBlank()) return Incoterm.FOB;
        try {
            return Incoterm.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Incoterm.FOB;
        }
    }
}
```

---

## 8. `ConversationServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.response.message.ConversationResponse;
import seekfactory.axoraa.entity.Conversation;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.repository.ConversationRepository;
import seekfactory.axoraa.services.ConversationService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages B2B buyer-supplier chat conversations.
 *
 * The listRecent method returns conversations ordered by last message time,
 * enriched with the manufacturer info so the frontend can render
 * factory avatars, names, and verification badges in the chat list.
 *
 * This matches the frontend MessageRepository.listRecent() contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ConversationResponse> listRecent(String userId, int limit) {
        List<Conversation> conversations = conversationRepository
                .findByBuyerIdOrderByLastMessageAtDesc(userId);

        // Apply limit
        if (limit > 0 && conversations.size() > limit) {
            conversations = conversations.subList(0, limit);
        }

        return conversations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ConversationResponse mapToResponse(Conversation conversation) {
        Manufacturer manufacturer = conversation.getManufacturer();

        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        return ConversationResponse.builder()
                .id(conversation.getId())
                .manufacturerId(manufacturer.getId())
                .lastMessage(conversation.getLastMessageText())
                .lastMessageAt(conversation.getLastMessageAt() != null
                        ? conversation.getLastMessageAt().toString() : null)
                .unreadCount(conversation.getUnreadCountBuyer())
                .manufacturer(mfgResponse)
                .build();
    }
}
```

---

## 9. `NotificationServiceImpl.java`

```java
package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.response.notification.NotificationResponse;
import seekfactory.axoraa.entity.Notification;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.services.NotificationService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages user notifications.
 *
 * Notifications are created by other services (RfqService, ConversationService)
 * when events occur (new quote received, new message, new follower).
 * This service handles retrieval and read-status management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listByUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadForUser(userId);
        log.info("Marked all notifications as read for user: {}", userId);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .createdAt(notification.getCreatedAt().toString())
                .read(notification.getIsRead())
                .build();
    }
}
```

---

> **All 10 service implementations are now complete** (AuthServiceImpl in Part 2 + these 9).
> 
> **Next: Part 4 — All 11 Controller classes.**
