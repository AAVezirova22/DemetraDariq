package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Page<Photo> findByUploadedById(Long userId, Pageable pageable);
}
