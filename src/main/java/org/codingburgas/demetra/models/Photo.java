package org.codingburgas.demetra.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "photos")
@Getter
@NoArgsConstructor
public class Photo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false, length = 30)
    private String contentType;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User uploadedBy;
    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public Photo(String title, String description, String contentType, User uploadedBy) {
        this.title = title; this.description = description;
        this.contentType = contentType; this.uploadedBy = uploadedBy;
    }
}
