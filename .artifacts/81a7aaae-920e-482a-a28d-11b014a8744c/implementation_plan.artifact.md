# Smart Navigation for Job Handshake

This plan implements automatic navigation (smart transitions) for both Workers and Clients when a job handshake begins.

## Proposed Changes

### Worker Side

#### [MODIFY] [WorkerDashboardActivity.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/WorkerDashboardActivity.kt)
- Add a public method `switchTab(tabId: Int)` that allows fragments to programmatically change the active tab.

#### [MODIFY] [WorkerFeedFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerFeedFragment.kt)
- After a successful job acceptance transaction in `processJobAcceptance`, call `switchTab(R.id.nav_job)` to move the worker to their active job screen immediately.

---

### Client Side

#### [MODIFY] [ClientDashboardActivity.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/ClientDashboardActivity.kt)
- Add a public method `switchTab(tabId: Int)` to programmatically change the active tab.
- Implement a `ListenerRegistration` that monitors the current user's jobs.
- If a job status changes to `IN_PROGRESS` while the user is on a different tab, automatically switch to `R.id.nav_requests`.

## Verification Plan

### Manual Verification
1.  **Worker Auto-Nav**: As a worker, accept a job from the Map Feed. Verify that the app immediately switches you to the "Job" tab.
2.  **Client Auto-Nav**: Log in as a client and stay on the "Home" tab. As a worker, accept that client's job. Verify that the client's app automatically switches to the "Requests" tab without user interaction.
