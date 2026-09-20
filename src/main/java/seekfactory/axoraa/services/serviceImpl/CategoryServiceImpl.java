package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.category.CategoryResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.services.services.CategoryService;

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
        // Resolve parent category by ID or slug
        Category parent = categoryRepository.findById(parentIdOrSlug)
                .or(() -> categoryRepository.findBySlug(parentIdOrSlug))
                .orElse(null);

        if (parent == null) {
            return List.of();
        }

        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(parent.getId());
        return children.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getBySlug(String slugOrId) {
        Category category = categoryRepository.findBySlug(slugOrId)
                .or(() -> categoryRepository.findById(slugOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug or id", slugOrId));
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