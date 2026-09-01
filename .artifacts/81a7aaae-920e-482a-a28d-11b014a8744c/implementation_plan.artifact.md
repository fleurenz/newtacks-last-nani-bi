# Transitioning Worker Account Header to Collapsing Toolbar

The user wants the blue banner in the `WorkerAccountFragment` to shrink when scrolling down to reveal more content.

## Proposed Changes

### UI Layout

#### [MODIFY] [fragment_worker_account.xml](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/res/layout/fragment_worker_account.xml)
- Change root to `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- Implement `com.google.android.material.appbar.AppBarLayout` and `com.google.android.material.appbar.CollapsingToolbarLayout`.
- Move the `layoutHeader` (blue banner) inside the `CollapsingToolbarLayout`.
- Replace the root `ScrollView` with `androidx.core.widget.NestedScrollView` and apply `app:layout_behavior="@string/appbar_scrolling_view_behavior"`.
- Add a `androidx.appcompat.widget.Toolbar` inside `CollapsingToolbarLayout` to act as the "sticky" header when collapsed.

### Fragment Logic

#### [MODIFY] [WorkerAccountFragment.kt](file:///C:/Users/FRUSCHE/StudioProjects/newtacks-last-nani-bi/app/src/main/java/com/example/newtacks/worker/WorkerAccountFragment.kt)
- Update view references if IDs change.
- Adjust status bar and padding logic to work with the new `CoordinatorLayout` structure if needed.

## Verification Plan

### Manual Verification
- Deploy to device.
- Open the Worker Account tab.
- Scroll down and observe the blue header shrinking and the name/avatar transitioning.
