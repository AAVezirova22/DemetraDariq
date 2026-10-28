package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.models.Photo;
import org.codingburgas.demetra.services.PhotoService;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.services.AppUserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PhotoController {
    private final PhotoService photos;
    public PhotoController(PhotoService photos) { this.photos = photos; }

    @GetMapping("/photos")
    public String gallery(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("photoPage", photos.gallery(page));
        return "photos/list";
    }

    @PostMapping("/photos")
    public String upload(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam(required = false) MultipartFile file, RedirectAttributes redirect) {
        try {
            Photo photo = photos.upload(principal.getUser(), title, description, file);
            redirect.addFlashAttribute("message", "Photo uploaded and saved to your profile.");
            return "redirect:/photos/" + photo.getId();
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            redirect.addFlashAttribute("title", title);
            redirect.addFlashAttribute("description", description);
            return "redirect:/photos";
        }
    }

    @GetMapping("/photos/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("photo", photos.open(id, principal.getUser()));
        return "photos/view";
    }

    @GetMapping("/photos/{id}/{variant:image|thumbnail}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable String variant) {
        PhotoService.ImageData content = photos.content(id, "thumbnail".equals(variant));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        var user = principal.getUser();
        model.addAttribute("profileUser", user);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        if (user.getRole() == Role.ADMIN) model.addAttribute("photoPage", photos.uploaded(user, page));
        else model.addAttribute("viewPage", photos.viewed(user, page));
        return "profile/index";
    }

}
