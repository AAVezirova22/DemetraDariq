package org.codingburgas.demetra.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Kept separately so gallery and profile queries never load the image bytes. */
@Entity
@Table(name = "photo_contents")
@Getter
@NoArgsConstructor
public class PhotoContent {
    @Id
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY) @MapsId
    private Photo photo;
    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] bytes;

    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] thumbnail;

    public PhotoContent(Photo photo, byte[] bytes, byte[] thumbnail) {
        this.photo = photo; this.bytes = bytes; this.thumbnail = thumbnail;
    }
}
