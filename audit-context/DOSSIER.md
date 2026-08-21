# Audit Context Dossier: Android/AppClone/TikTok posting path

## Scope and reading boundary

This dossier maps the visible posting workflow in `D:\Workspaces\clone app` only. It is an orientation record, not a bug verdict, severity assessment, or change proposal. The primary implementation path is the Android `AppClonerProject`; the PC-side `gdrive_uploader.py`, visible test scripts, and the clone/repackager modules are included where they establish inputs or assumptions consumed by the posting path.

The requested “updrive” path has no literal `updrive` identifier in the checkout. The closest visible producer/bridge is `gdrive_uploader.py`, which watches a PC export folder and uploads to Google Drive; the Android consumer is `GoogleDriveClient`.

## Coverage map

| Area | Read entry points | Function records |
| --- | --- | --- |
| PC producer and Drive queue | `gdrive_uploader.py` | `pc_init_auth`, `pc_upload_file`, `pc_upload_subai_video`, `pc_watch_folder` |
| Android Drive access and queue parsing | `GoogleDriveClient.kt` | `android_get_access_token`, `android_find_folder_id`, `android_list_queue_videos`, `android_download_video`, `android_mark_video_done`, `android_sync_clones` |
| Queue UI, clone choice, and task creation | `MainActivity.kt` | `main_sync_drive`, `main_show_schedule_dialog` |
| Clone package naming and split handoff | `CloneSettingsActivity.kt`, `ClonePipeline.kt`, `AxmlEditor.kt` | `clone_start_process`, `clone_install_files_for`, `clone_pipeline_execute`, `clone_modify_manifest` |
| Local schedule persistence and alarms | `ScheduleModel.kt`, `ScheduleStorage.kt`, `ScheduleAlarmReceiver.kt` | `schedule_model_to_json`, `schedule_model_from_json`, `schedule_get_tasks`, `schedule_add_task`, `schedule_update_task`, `schedule_alarm`, `schedule_alarm_receiver` |
| Download-to-post orchestration | `TikTokPostForegroundService.kt` | `post_service_start`, `post_service_execute` |
| Accessibility caption entry and publish interaction | `TikTokAutoPostService.kt` | `accessibility_start_session`, `accessibility_inspect_screen`, `accessibility_fill_caption`, `accessibility_find_caption`, `accessibility_find_next`, `accessibility_find_post`, `accessibility_click_post`, `accessibility_smart_click` |

Decompiled APK/native material and the general clone/runtime surface were not treated as the posting implementation. Visible test/docs references were used only to cross-check package/file conventions.

## End-to-end context

```text
PC SubAI export
  -> gdrive_uploader.watch_folder
  -> Google Drive SubAI_Queue: video.mp4 + video.mp4.json
  -> MainActivity manual sync
  -> listQueueVideos joins JSON metadata to video by video_file_name
  -> schedule dialog selects an installed/fallback package and edits caption/hashtags
  -> ScheduleTask in SharedPreferences + exact AlarmManager alarm
  -> ScheduleAlarmReceiver starts TikTokPostForegroundService
  -> downloadVideoToFile into cache
  -> ACTION_SEND video to task.targetPackageName
  -> TikTokAutoPostService scans the active accessibility tree
  -> caption text entry, Next/Post node selection, delayed click
  -> local task status update and (when status is COMPLETED) Drive video move to SubAI_Done
```

The Android side does not show a periodic Drive poll. `syncVideosFromGoogleDrive` is reached from the manual sync button and after saving Drive configuration (`MainActivity.kt:L384-L391`, `L480-L491`); the scheduled path downloads by stored Drive file ID at alarm time (`TikTokPostForegroundService.kt:L54-L74`).

## Cross-project data contracts

### Drive queue and metadata

- The PC uploader creates/fetches folders named `SubAI_Queue` and `SubAI_Done` (`gdrive_uploader.py:L214-L219`).
- A video upload stores the video’s Drive ID, basename, title, caption, hashtags, target clone, suggested time, and creation time in a sibling JSON object (`gdrive_uploader.py:L273-L296`).
- Android lists up to 50 non-trashed files in the queue folder and treats `.mp4` or `video/*` entries as videos; JSON files are downloaded and keyed by their `video_file_name` (`GoogleDriveClient.kt:L213-L241`).
- The join is by filename, not the metadata’s `video_file_id`; missing or malformed metadata is silently skipped and fallback caption/hashtags/clone values are supplied (`GoogleDriveClient.kt:L229-L256`).
- Completion moves only the video file from the queue folder to the done folder; the metadata file is not moved by this path (`GoogleDriveClient.kt:L331-L341`).

### Clone identity and routing

- UI clone creation derives the package from the selected source package as `${original}.clone${number}` (`CloneSettingsActivity.kt:L184-L187`). The CLI TikTok fixture uses `com.ss.android.ugc.trill` -> `com.ss.android.ugc.trill.clone1` (`CliCloner.kt:L38-L53`).
- The package editor rewrites the base and each split manifest using the same new package (`ClonePipeline.kt:L154-L162`, `L310-L317`).
- Installed-clone cloud sync recognizes packages beginning with `com.ss.android.ugc.trill` and publishes an `id` equal to `base` or the suffix after the base package (`GoogleDriveClient.kt:L356-L375`).
- The queue metadata’s `target_clone` is shown in the video list (`MainActivity.kt:L618-L623`), but schedule creation derives `targetPackageName` from the user’s spinner selection (`MainActivity.kt:L561-L583`). No code in the visible path maps `target_clone` directly to that package.

### Local task state

`ScheduleTask` persists the Drive file ID, target package, caption/hashtags, scheduled time, status, progress, log text, retry count, and creation time as JSON (`ScheduleModel.kt:L35-L50`). The task ID is an eight-character random UUID prefix created at scheduling time (`MainActivity.kt:L575-L584`). The state machine is `PENDING -> DOWNLOADING -> POSTING -> COMPLETED|FAILED`; `CANCELLED` is defined but the visible cancel action deletes the task instead (`ScheduleModel.kt:L8-L15`, `MainActivity.kt:L370-L377`).

## System-spanning assumptions and observations

These are continuity facts for the next audit phase; they are not classifications.

1. **Queue readiness is local and filename-based.** The PC watcher uses a two-second unchanged-size check before uploading (`gdrive_uploader.py:L314-L321`), while Android accepts any matching video/JSON pair returned by the queue query. The Android query has `pageSize=50` and no visible next-page traversal (`GoogleDriveClient.kt:L213-L220`).
2. **Duplicate tracking exists only in narrow local scopes.** The PC watcher’s `processed_files` is an in-memory set (`gdrive_uploader.py:L306-L333`). Android collapses returned videos with `distinctBy { it.fileName }` (`GoogleDriveClient.kt:L271-L274`), and schedule IDs are newly generated (`MainActivity.kt:L575-L584`). No visible task uniqueness key is derived from the Drive file ID.
3. **Metadata is advisory at scheduling time.** `target_clone` and `schedule_time` are parsed into `DriveVideoItem` (`GoogleDriveClient.kt:L251-L268`), but the dialog initializes its own time to five minutes ahead and builds its own target list (`MainActivity.kt:L512-L535`).
4. **Publish completion is a local automation state.** With accessibility disabled, the foreground service sets the task to `COMPLETED` after attempting to open the target package (`TikTokPostForegroundService.kt:L119-L160`). With accessibility enabled, the service reports success after a delayed click and a three-second wait without reading a post-result marker (`TikTokAutoPostService.kt:L211-L228`).
5. **Retry scope is exception-driven.** The foreground service retries thrown exceptions up to five times, schedules each retry three minutes out, and otherwise marks the task failed (`TikTokPostForegroundService.kt:L162-L175`). A `false` completion callback is assigned `FAILED` inside the callback (`TikTokPostForegroundService.kt:L135-L147`) rather than entering that exception retry block.
6. **Remote acknowledgement follows the local status.** The `finally` block deletes the cached file and calls `markVideoAsDone` only when the local task status is `COMPLETED`; the Boolean result is ignored and the local task is persisted afterward (`TikTokPostForegroundService.kt:L176-L194`).
7. **Alarm recovery is not established by the visible load path.** `getTasks` loads cached JSON and returns it (`ScheduleStorage.kt:L22-L29`); exact alarms are registered in `addTask` and the retry branch (`ScheduleStorage.kt:L36-L43`, `TikTokPostForegroundService.kt:L164-L170`). No startup/reload caller that re-registers all pending alarms was found in the Android source search.

## Key files

- [gdrive_uploader.py](D:/Workspaces/clone%20app/gdrive_uploader.py): PC queue producer, metadata writer, and export-folder polling loop.
- [GoogleDriveClient.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/gdrive/GoogleDriveClient.kt): Android token, queue listing, metadata parsing, download, clone sync, and Drive acknowledgement.
- [MainActivity.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/activity/MainActivity.kt): manual sync, target clone selection, caption/hashtag editing, and task creation.
- [ScheduleStorage.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/scheduler/ScheduleStorage.kt): SharedPreferences task cache and AlarmManager registration.
- [TikTokPostForegroundService.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/scheduler/TikTokPostForegroundService.kt): scheduled download, share routing, accessibility session, retry, cleanup, and Drive move.
- [TikTokAutoPostService.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/service/TikTokAutoPostService.kt): accessibility-tree polling, text entry, node detection, click, and completion callback.
- [CloneSettingsActivity.kt](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/java/com/cloner/app/activity/CloneSettingsActivity.kt), [ClonePipeline.kt](D:/Workspaces/clone%20app/AppClonerProject/core-repackager/src/main/java/com/cloner/repackager/ClonePipeline.kt), [AxmlEditor.kt](D:/Workspaces/clone%20app/AppClonerProject/core-repackager/src/main/java/com/cloner/repackager/axml/AxmlEditor.kt): package naming and base/split output used by target clone discovery and routing.
- [AndroidManifest.xml](D:/Workspaces/clone%20app/AppClonerProject/app/src/main/AndroidManifest.xml): registration of the schedule receiver, foreground service, and accessibility service (`L81-L104`).

## Open questions for the next phase

- Is the intended “updrive” component only `gdrive_uploader.py`, or is another producer outside this checkout expected? No `updrive` symbol was found here.
- Does the Drive queue contract guarantee one unique video filename per queue, and are duplicate same-name files possible? Android joins by filename and retains the first metadata object for a name (`GoogleDriveClient.kt:L229-L237`, `L251-L274`).
- Is the 50-file queue limit intentional, and where is pagination or queue ordering defined? The Android request asks for 50 files and reads one response page (`GoogleDriveClient.kt:L213-L220`).
- Should `target_clone` and `schedule_time` from the PC metadata be authoritative, or is the Android dialog intentionally user-driven? Both fields are parsed, but only displayed/retained as suggestions (`GoogleDriveClient.kt:L255-L267`, `MainActivity.kt:L512-L583`).
- How is an installed clone expected to be selected when its label contains parentheses, or when the package contains `tiktok` but does not use the `com.ss.android.ugc.trill` prefix? The UI filter and package parser are visible at `MainActivity.kt:L512-L567`; the cloud sync filter is narrower at `GoogleDriveClient.kt:L356-L360`.
- What external evidence is the publish confirmation supposed to be? The visible completion paths are a target launch or a delayed click callback; no TikTok post ID, confirmation screen, or server acknowledgement is read (`TikTokPostForegroundService.kt:L108-L160`, `TikTokAutoPostService.kt:L216-L227`).
- Are pending tasks re-armed after process/device restart, and how are stale `DOWNLOADING`/`POSTING` records recovered? The visible load path only parses the persisted list (`ScheduleStorage.kt:L66-L79`).
- Is a retry after a post-side exception allowed to reuse the same source video, and what source-side state distinguishes an upload in progress from a completed post? The retry path reuses `driveFileId` and only remote-moves on local `COMPLETED` (`TikTokPostForegroundService.kt:L162-L194`).
- Is the `SubAI_Done` move itself the complete source acknowledgement, or should metadata/state also move or be updated? The visible acknowledgement is a PATCH of the video’s parents; its Boolean result is not fed back into task state (`GoogleDriveClient.kt:L331-L341`, `TikTokPostForegroundService.kt:L185-L192`).
- Is OAuth configuration expected on Android? `GoogleDriveClient` supports refresh-token and service-account JSON (`GoogleDriveClient.kt:L86-L174`), while the visible configuration dialog accepts only JSON containing `private_key` and `client_email` (`MainActivity.kt:L480-L486`).
