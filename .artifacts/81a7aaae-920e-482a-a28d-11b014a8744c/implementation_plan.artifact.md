# Real-time Location Tracking Implementation

This plan enables continuous location synchronization between Workers and Clients during an active job handshake.

## User Review Required

> [!IMPORTANT]
> - Users must keep the app open (at least in the foreground/dashboard) for updates to occur.
> - Battery consumption will increase slightly while a job is active due to periodic GPS usage.
> - Updates are pushed to Firestore every 10 seconds only when a job is in a handshake state (`IN_PROGRESS`, `HEADING_TO_CLIENT`, or `ARRIVED`).

## Proposed Changes

### Dashboards (Background Sync)

#### [MODIFY] [WorkerDashboardActivity.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/WorkerDashboardActivity.kt)
- Initialize `FusedLocationProviderClient`.
- Add `startLocationUpdates()` that pushes the worker's current coordinates to their Firestore `user` document.
- Implement a listener that starts/stops these updates based on whether the worker has an active job.

#### [MODIFY] [ClientDashboardActivity.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/ClientDashboardActivity.kt)
- Mirror the worker logic: Push client coordinates to Firestore only when a request is active and a worker is assigned.

### Handshake Screens (UI Tracking)

#### [MODIFY] [WorkerJobFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerJobFragment.kt)
- Add a `clientLocationListener` that monitors the client's `user` document.
- Update the `tvStatus` text to show the distance between the worker and the client (e.g., "Arrived at Location (0.1 km away)").

## Verification Plan

### Manual Verification
1.  **Handshake Start**: As a worker, accept a job and click "Start Heading There".
2.  **Worker Movement**: Move the worker device. Verify the client device updates the distance text in real-time.
3.  **Client Movement**: Move the client device. Verify the worker device updates the distance text in real-time.
4.  **Completion**: Mark the job as "Done". Verify that location updates stop (battery saving).
