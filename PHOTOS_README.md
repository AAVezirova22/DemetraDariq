# Photo gallery and profiles

After restarting the app, sign in and use **Photos** (`/photos`) or **My profile** (`/profile`) in the navigation.

- Administrators can upload JPEG and PNG photos, add a title and description, and view their own **Uploaded photos** in their admin profile.
- Teachers (the existing `ORGANIZER` role) and students can browse the gallery and open full photos. Opening a photo adds it to their own **Viewed photos**. Reopening it updates its last-viewed time without creating a duplicate.
- Gallery thumbnails do not add viewed history. Profiles only show the signed-in user's history.
- Photos, thumbnails, uploader links, and viewing history are stored in the configured database. MySQL data persists in the existing Docker database volume; the `demo` profile and automated tests use temporary databases.

## First administrator

On startup, if there is no administrator, the app creates **admin@demetra.local** with the `ADMIN` role. A randomly generated initial password appears **once** in the server startup logs under `Initial admin password`. Use the existing `/login` page, then open `/profile` or `/photos`.

You can instead choose the first account's email and password before starting the app:

```sh
export DEMETRA_ADMIN_EMAIL='your-admin@example.com'
read -s 'DEMETRA_ADMIN_PASSWORD?Initial admin password (12–72 characters): '
export DEMETRA_ADMIN_PASSWORD
# Start the app using your usual Maven or Docker Compose command.
```

Both Compose configurations pass these environment variables to the app. Passwords are BCrypt hashes in the database. An existing administrator is preserved on restart; changing these variables does not reset accounts. An email already used by a non-admin must be replaced with an unused email. Public registration only creates student or teacher accounts, even if a request submits `ADMIN`.

## MVC package structure

Photo functionality follows the application's existing MVC layers:

- `controllers/PhotoController.java`: gallery, upload, image, and profile routes.
- `controllers/PhotoUploadAdvice.java`: oversized upload error handling.
- `services/PhotoService.java`: image validation, storage, thumbnails, and viewed history.
- `repositories/PhotoRepository.java`, `PhotoContentRepository.java`, and `PhotoViewRepository.java`: database access.
- `models/Photo.java`, `PhotoContent.java`, and `PhotoView.java`: persistent entities.
- `config/AdminAccountInitializer.java`: first-admin setup.

All Java files above are under `src/main/java/org/codingburgas/demetra/`. There is no separate Java `photos` package. Views use the existing Thymeleaf convention under `src/main/resources/templates/photos/` and `templates/profile/`; gallery styling is in `static/css/photos.css`.

## Limits and access

Uploads require both an authenticated administrator and a valid CSRF token. Each photo must be no larger than 5 MB, no larger than 20 megapixels, and no more than 10000 pixels on either side. The server decodes actual image data and re-encodes it to remove metadata; renaming another file to `.png` does not bypass validation. The gallery and profiles show 24 photos per page. Original images and thumbnails require authentication.

## Validation

```sh
mvn -o test
```

`PhotoFlowTests` covers upload permissions, real PNG/JPEG decoding, thumbnails, persisted image retrieval, profile ownership, repeated views, invalid and oversized uploads, CSRF, authenticated image access, first-admin login, public role escalation, missing photos, and pagination. The existing event, authentication, and reporting tests remain in the suite.
