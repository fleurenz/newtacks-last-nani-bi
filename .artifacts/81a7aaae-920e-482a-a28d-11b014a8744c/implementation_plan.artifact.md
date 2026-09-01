# Prevent Duplicate Hiring Applications

This plan adds logic to prevent workers from applying to the same company hiring post multiple times. The UI will update to show "Applied" or "Already Applied" if an application has already been submitted.

## User Review Required

> [!NOTE]
> The "Already Applied" state will be reflected both in the feed list and the detailed preview dialog.
> Firestore `arrayUnion` already prevents duplicate IDs in the `applicants` list, but this change improves the user experience by providing clear feedback.

## Proposed Changes

### Adapters

#### [MODIFY] [HiringAdapter.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/HiringAdapter.kt)
- Add `currentUserId: String?` to the constructor.
- In `onBindViewHolder`, check if `post.applicants` contains `currentUserId`.
- If applied, change `btnAccept` text to "Applied", disable it, and reduce alpha.

### Worker Feature

#### [MODIFY] [WorkerFeedFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerFeedFragment.kt)
- Update `hiringAdapter` initialization to pass the current user's UID.
- In `showHiringPreview`, check if the user has already applied.
- If applied, change `btnAccept` text to "Already Applied" and disable it.

## Verification Plan

### Automated Tests
- Build the project to ensure all constructor changes and logic are correct.

### Manual Verification
- **Apply to Job**: As a worker, apply to a company hiring post.
- **Verify Feed**: Confirm that the button in the hiring feed list now says "Applied" and is disabled for that specific post.
- **Verify Preview**: Click the post anyway (to open the preview) and confirm the "Apply Now" button is replaced by "Already Applied" and disabled.
