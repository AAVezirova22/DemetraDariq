package org.codingburgas.demetra.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_views", uniqueConstraints = @UniqueConstraint(columnNames = {"viewer_id", "photo_id"}))
@Getter
@NoArgsConstructor
public class PhotoView {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User viewer;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Photo photo;
    @Column(nullable = false)
    private LocalDateTime viewedAt = LocalDateTime.now();

    public PhotoView(User viewer, Photo photo) { this.viewer = viewer; this.photo = photo; }
    public void viewedNow() { viewedAt = LocalDateTime.now(); }
}
