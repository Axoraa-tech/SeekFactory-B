package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIsNullOrderByNameAsc(); //Root categories

    List<Category> findByParentIdOrderByNameAsc(String parentId); // Children of a parent

    List<Category> findByParentSlugOrderByNameAsc(String slug);   // Children by parent slug

}
