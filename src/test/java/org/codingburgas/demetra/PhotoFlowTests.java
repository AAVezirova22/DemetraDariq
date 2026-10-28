package org.codingburgas.demetra;

import org.codingburgas.demetra.config.AdminAccountInitializer;
import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.repositories.PhotoRepository;
import org.codingburgas.demetra.repositories.PhotoContentRepository;
import org.codingburgas.demetra.repositories.PhotoViewRepository;
import org.codingburgas.demetra.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoFlowTests {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired PhotoRepository photoRepository;
    @Autowired PhotoContentRepository contents;
    @Autowired PhotoViewRepository views;
    @Autowired PhotoService photos;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminAccountInitializer adminInitializer;
    private User admin, student, teacher;

    @BeforeEach
    void accounts() {
        String suffix = UUID.randomUUID().toString();
        admin = users.register("Gallery Admin", "admin-" + suffix + "@example.com", "Passw0rd", Role.ADMIN);
        student = users.register("Gallery Student", "student-" + suffix + "@example.com", "Passw0rd", Role.STUDENT);
        teacher = users.register("Gallery Teacher", "teacher-" + suffix + "@example.com", "Passw0rd", Role.ORGANIZER);
    }

    private MockMultipartFile image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), format, output);
        return new MockMultipartFile("file", "photo." + format, "image/" + format, output.toByteArray());
    }

    private Photo upload(String title) throws Exception {
        var result = mvc.perform(multipart("/photos").file(image("png"))
                        .param("title", title).param("description", "Community recital")
                        .with(user(new AppUserPrincipal(admin))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String path = result.getResponse().getRedirectedUrl();
        assertNotNull(path);
        assertTrue(path.matches("/photos/\\d+"));
        return photoRepository.findById(Long.valueOf(path.substring("/photos/".length()))).orElseThrow();
    }

    @Test
    void adminUploadsAndContentIsPersistedWithThumbnail() throws Exception {
        Photo photo = upload("Recital <photo>");
        assertEquals(admin.getId(), photo.getUploadedBy().getId());
        assertTrue(contents.existsById(photo.getId()));
        mvc.perform(get("/profile").with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Admin profile")))
                .andExpect(content().string(containsString("Uploaded photos")))
                .andExpect(content().string(containsString("Recital &lt;photo&gt;")));
        mvc.perform(get("/photos/" + photo.getId()).with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isOk());
        assertTrue(views.findByViewerIdAndPhotoId(admin.getId(), photo.getId()).isEmpty());
        var response = mvc.perform(get("/photos/" + photo.getId() + "/image").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        assertEquals(800, decoded.getWidth());
        var preview = mvc.perform(get("/photos/" + photo.getId() + "/thumbnail").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(640, ImageIO.read(new java.io.ByteArrayInputStream(preview)).getWidth());
        assertTrue(views.findByViewerIdAndPhotoId(student.getId(), photo.getId()).isEmpty());
    }

    @Test
    void studentsAndTeachersBrowseAndSaveUniquePrivateViewedHistory() throws Exception {
        String title = "Private history " + UUID.randomUUID();
        Photo photo = upload(title);
        for (User viewer : new User[]{student, teacher}) {
            mvc.perform(get("/photos").with(user(new AppUserPrincipal(viewer))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString(title)))
                    .andExpect(content().string(not(containsString("enctype=\"multipart/form-data\""))));
            assertTrue(views.findByViewerIdAndPhotoId(viewer.getId(), photo.getId()).isEmpty());
            mvc.perform(get("/photos/" + photo.getId()).with(user(new AppUserPrincipal(viewer))))
                    .andExpect(status().isOk());
            var firstView = views.findByViewerIdAndPhotoId(viewer.getId(), photo.getId()).orElseThrow();
            mvc.perform(get("/photos/" + photo.getId()).with(user(new AppUserPrincipal(viewer))))
                    .andExpect(status().isOk());
            var secondView = views.findByViewerIdAndPhotoId(viewer.getId(), photo.getId()).orElseThrow();
            assertEquals(firstView.getId(), secondView.getId());
            assertFalse(secondView.getViewedAt().isBefore(firstView.getViewedAt()));
            mvc.perform(get("/profile").with(user(new AppUserPrincipal(viewer))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Viewed photos")))
                    .andExpect(content().string(containsString(title)));
        }
        User other = users.register("Unrelated student", UUID.randomUUID() + "@example.com", "Passw0rd", Role.STUDENT);
        mvc.perform(get("/profile").param("userId", student.getId().toString()).with(user(new AppUserPrincipal(other))))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(title))));
        mvc.perform(get("/profile").with(user(new AppUserPrincipal(student))))
                .andExpect(content().string(not(containsString("Gallery Teacher"))));
    }

    @Test
    void nonAdminsCannotUploadEvenByCallingServiceDirectly() throws Exception {
        long before = photoRepository.count();
        for (User viewer : new User[]{student, teacher}) {
            mvc.perform(multipart("/photos").file(image("png")).param("title", "Forbidden")
                            .with(user(new AppUserPrincipal(viewer))).with(csrf()))
                    .andExpect(status().isForbidden());
            assertThrows(AccessDeniedException.class, () -> photos.upload(viewer, "Forbidden", "", image("png")));
        }
        mvc.perform(multipart("/photos").file(image("png")).param("title", "No CSRF")
                        .with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isForbidden());
        assertEquals(before, photoRepository.count());
    }

    @Test
    void anonymousCannotAccessGalleryProfilesOrImageData() throws Exception {
        Photo photo = upload("Restricted photo");
        for (String path : new String[]{"/photos", "/profile", "/photos/" + photo.getId(),
                "/photos/" + photo.getId() + "/image", "/photos/" + photo.getId() + "/thumbnail"}) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
        mvc.perform(multipart("/photos").file(image("png")).param("title", "Anonymous").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void rejectsInvalidFilesAndMetadataWithoutSaving() throws Exception {
        long before = photoRepository.count();
        var invalid = new MockMultipartFile("file", "fake.png", "image/png", "<script>alert(1)</script>".getBytes());
        var large = new MockMultipartFile("file", "big.png", "image/png", new byte[PhotoService.MAX_BYTES + 1]);
        for (var file : new MockMultipartFile[]{invalid, large, new MockMultipartFile("file", new byte[0])}) {
            mvc.perform(multipart("/photos").file(file).param("title", "Invalid photo")
                            .with(user(new AppUserPrincipal(admin))).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/photos"))
                    .andExpect(flash().attributeExists("error"));
        }
        for (String title : new String[]{" ", "a".repeat(161)}) {
            mvc.perform(multipart("/photos").file(image("png")).param("title", title)
                            .with(user(new AppUserPrincipal(admin))).with(csrf()))
                    .andExpect(redirectedUrl("/photos")).andExpect(flash().attributeExists("error"));
        }
        assertThrows(IllegalArgumentException.class, () -> photos.upload(admin, "Title", "x".repeat(2001), image("png")));
        assertEquals(before, photoRepository.count());
        Photo jpeg = photos.upload(admin, "JPEG supported", "", image("jpeg"));
        assertEquals("image/jpeg", photos.content(jpeg.getId(), false).contentType());
    }

    @Test
    void missingPhotosReturn404AndEmptyProfilesRender() throws Exception {
        mvc.perform(get("/photos/9223372036854775807").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/photos/9223372036854775807/image").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/profile").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("You have not viewed any photos yet")));
        mvc.perform(get("/profile").with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("You have not uploaded any photos yet")));
    }

    @Test
    void bootstrapAdminCanLoginAndPublicRegistrationCannotEscalate() throws Exception {
        User seeded = userRepository.findByEmail("admin-test@example.com").orElseThrow();
        assertEquals(Role.ADMIN, seeded.getRole());
        assertTrue(encoder.matches("AdminTestPassword123", seeded.getPasswordHash()));
        adminInitializer.run();
        assertEquals(seeded.getPasswordHash(), userRepository.findById(seeded.getId()).orElseThrow().getPasswordHash());
        mvc.perform(post("/login").with(csrf()).param("username", seeded.getEmail()).param("password", "AdminTestPassword123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/events"));
        String email = "no-escalation-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/register").with(csrf()).param("name", "Public user").param("email", email)
                        .param("password", "Passw0rd").param("role", "ADMIN"))
                .andExpect(redirectedUrl("/login?registered"));
        assertEquals(Role.STUDENT, userRepository.findByEmail(email).orElseThrow().getRole());
    }

    @Test
    void simultaneousOpensCreateOneHistoryEntry() throws Exception {
        Photo photo = upload("Concurrent views");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Long> open = () -> {
                start.await();
                return photos.open(photo.getId(), student).getId();
            };
            var first = executor.submit(open);
            var second = executor.submit(open);
            start.countDown();
            assertEquals(photo.getId(), first.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(photo.getId(), second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, photos.viewed(student, 0).getTotalElements());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void paginatedGalleryAndProfileRenderNextAndPreviousLinks() throws Exception {
        for (int i = 0; i < 25; i++) {
            photoRepository.save(new Photo("Page photo " + i, "", "image/png", admin));
        }
        mvc.perform(get("/photos").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/photos?page=1")));
        mvc.perform(get("/profile").with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/profile?page=1")));
        mvc.perform(get("/profile").param("page", "1").with(user(new AppUserPrincipal(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/profile?page=0")));
    }
}
