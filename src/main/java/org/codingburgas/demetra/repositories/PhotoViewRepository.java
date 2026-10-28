package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.PhotoView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PhotoViewRepository extends JpaRepository<PhotoView, Long> {
    Optional<PhotoView> findByViewerIdAndPhotoId(Long viewerId, Long photoId);
    @EntityGraph(attributePaths = "photo")
    Page<PhotoView> findByViewerId(Long userId, Pageable pageable);
}
