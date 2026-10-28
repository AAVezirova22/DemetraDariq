package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Photo;
import org.codingburgas.demetra.models.PhotoContent;
import org.codingburgas.demetra.models.PhotoView;
import org.codingburgas.demetra.repositories.PhotoRepository;
import org.codingburgas.demetra.repositories.PhotoContentRepository;
import org.codingburgas.demetra.repositories.PhotoViewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.*;
import java.util.Iterator;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PhotoService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final PhotoRepository photos;
    private final PhotoContentRepository contents;
    private final PhotoViewRepository views;
    private final EntityManager entityManager;

    public PhotoService(PhotoRepository photos, PhotoContentRepository contents,
                        PhotoViewRepository views, EntityManager entityManager) {
        this.photos = photos; this.contents = contents; this.views = views; this.entityManager = entityManager;
    }

    public Page<Photo> gallery(int page) {
        return photos.findAll(PageRequest.of(Math.max(0, page), 24, Sort.by("uploadedAt", "id").descending()));
    }

    public Page<Photo> uploaded(User user, int page) {
        return photos.findByUploadedById(user.getId(), PageRequest.of(Math.max(0, page), 24,
                Sort.by("uploadedAt", "id").descending()));
    }

    public Page<PhotoView> viewed(User user, int page) {
        return views.findByViewerId(user.getId(), PageRequest.of(Math.max(0, page), 24,
                Sort.by("viewedAt", "id").descending()));
    }

    public Photo find(Long id) {
        return photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record ImageData(String contentType, byte[] bytes) {}

    public ImageData content(Long id, boolean thumbnail) {
        PhotoContent content = contents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new ImageData(content.getPhoto().getContentType(), thumbnail ? content.getThumbnail() : content.getBytes());
    }

    @Transactional
    public Photo open(Long id, User user) {
        // Lock before other reads so MySQL's repeatable-read snapshot includes prior opens.
        User viewer = user.getRole() == Role.ADMIN ? null
                : entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        Photo photo = find(id);
        if (viewer != null) {
            PhotoView view = views.findByViewerIdAndPhotoId(viewer.getId(), id)
                    .orElseGet(() -> new PhotoView(viewer, photo));
            view.viewedNow();
            views.save(view);
        }
        return photo;
    }

    @Transactional
    public Photo upload(User user, String title, String description, MultipartFile file) {
        if (user.getRole() != Role.ADMIN) throw new AccessDeniedException("Only administrators can upload photos.");
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        if (title.isEmpty() || title.length() > 160) throw new IllegalArgumentException("Title must be between 1 and 160 characters.");
        if (description.length() > 2000) throw new IllegalArgumentException("Description must be 2000 characters or less.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a photo to upload.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Photos must be 5 MB or smaller.");

        // Decode actual image data; filenames and browser-supplied MIME types are untrusted.
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a valid JPEG or PNG photo.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw new IllegalArgumentException("Choose a JPEG or PNG photo.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 20_000_000)
                    throw new IllegalArgumentException("Photos must be at most 20 megapixels and 10000 pixels per side.");
                BufferedImage image = reader.read(0);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                // Re-encode to remove embedded metadata and any trailing non-image data.
                if (!ImageIO.write(image, format, output)) throw new IOException("Unsupported image");
                byte[] bytes = output.toByteArray();
                if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("The processed photo exceeds 5 MB. Choose a smaller photo.");
                Photo photo = photos.save(new Photo(title, description, "image/" + format, user));
                double scale = Math.min(1.0, 640.0 / Math.max(width, height));
                BufferedImage thumbnail = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = thumbnail.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(image, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null);
                } finally { graphics.dispose(); }
                ByteArrayOutputStream preview = new ByteArrayOutputStream();
                ImageIO.write(thumbnail, format, preview);
                contents.save(new PhotoContent(photo, bytes, preview.toByteArray()));
                return photo;
            } finally { reader.dispose(); }
        } catch (IOException ex) {
            throw new IllegalArgumentException("This photo could not be read. Choose a valid JPEG or PNG photo.");
        }
    }
}
