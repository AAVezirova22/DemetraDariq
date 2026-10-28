package org.codingburgas.demetra.controllers;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Multipart parsing can fail before Spring selects a controller. */
@ControllerAdvice
public class PhotoUploadAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Photos must be 5 MB or smaller.");
        return "redirect:/photos";
    }
}
