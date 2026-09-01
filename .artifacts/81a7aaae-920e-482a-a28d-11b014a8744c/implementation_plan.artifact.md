# Implement "Navigate to Map" from Active Job

This plan adds a "Navigate" button to the active job screen for workers. Clicking it will switch the view to the Map Feed and zoom directly into the job's location.

## Proposed Changes

### UI Layout

#### [MODIFY] [fragment_worker_job.xml](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/res/layout/fragment_worker_job.xml)
- Add a new `MaterialButton` (ID: `btnNavigateToMap`) in the `layoutBottomButtons` section.
- This button will be styled with an outline to differentiate it from the main status action buttons.

### Navigation Logic

#### [MODIFY] [WorkerDashboardActivity.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/WorkerDashboardActivity.kt)
- Add a function `focusMapOnLocation(lat: Double, lng: Double)` that:
    1. Switches the tab to `nav_feed`.
    2. Calls a new `zoomToLocation` method on the `fragmentFeed`.

#### [MODIFY] [WorkerFeedFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerFeedFragment.kt)
- Add a public function `zoomToLocation(lat: Double, lng: Double)` that animates the map camera to the given coordinates.

#### [MODIFY] [WorkerJobFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerJobFragment.kt)
- Initialize `btnNavigateToMap`.
- Show/hide the button based on whether a job is active.
- Set a click listener that calls `focusMapOnLocation` using the current job's coordinates.

## Verification Plan

### Manual Verification
1.  **Accept a Job**: As a worker, accept a job from the feed.
2.  **Navigate**: On the Job tab, you should see a "Navigate to Job Site" button.
3.  **Click**: Tap the button. Verify that:
    - The bottom navigation switches back to the Feed tab.
    - The map automatically zooms into the job's pin location.
