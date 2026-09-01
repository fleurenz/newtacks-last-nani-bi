# Overhaul Worker Feed: Map-First Interface

This plan transforms the Worker Feed from a tab-based interface to a map-centric view with a toggleable list for navigation.

## User Review Required

> [!IMPORTANT]
> - The worker's name and the "List Feed/Map View" tabs will be removed.
> - The Map is now the primary interface.
> - Clicking an item in the list will **zoom the map** to that location instead of showing the job details immediately.
> - Job details will appear only when the user **taps a pin** on the map.

## Proposed Changes

### UI Layout

#### [MODIFY] [fragment_worker_feed.xml](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/res/layout/fragment_worker_feed.xml)
- Replace root with `androidx.constraintlayout.widget.ConstraintLayout`.
- Set `MapView` to fill the entire screen.
- Add a `FloatingActionButton` (ID: `fabToggleList`) at the top right.
- Add a `MaterialCardView` (ID: `cardListOverlay`) that overlays the map, containing the jobs `RecyclerView`. This will be hidden by default.

#### [MODIFY] [item_worker_job.xml](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/res/layout/item_worker_job.xml)
- Change `btnAccept` text from "View Details" to "Find on Map" to match the new behavior.

### Fragment Logic

#### [MODIFY] [WorkerFeedFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerFeedFragment.kt)
- Remove `tabLayout` and `btnWorkerProfile` logic.
- Implement toggle logic for `cardListOverlay` via `fabToggleList`.
- Update `WorkerJobAdapter` listener:
    - Close the list overlay.
    - Animate map camera to the job's LatLng with zoom.
- In `initMap`:
    - Add `setOnMarkerClickListener`.
    - When a marker is clicked, identify the associated `Job` and call `showJobPreview(job)`.

## Verification Plan

### Manual Verification
1.  **Open Feed**: Verify the map opens full-screen immediately.
2.  **Toggle List**: Tap the top-right button. The job list should appear over the map.
3.  **Navigate**: Tap a job in the list. The list should disappear, and the map should zoom into that job's pin.
4.  **View Info**: Tap the pin on the map. The job details dialog (preview) should appear.
