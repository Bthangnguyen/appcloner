# Drive queue contract v1

## Transport

- `SubAI_Queue` contains videos and their JSON sidecars. Queue subfolders are
  allowed for channel routing.
- `SubAI_Done` is the acknowledgement boundary. The Android executor moves the
  video and its sidecar there only after TikTok confirmation.
- `device_clones.json` is a registry published by Android and consumed by the
  desktop UI. It is not a job record.

## Compatibility

- A missing `schema_version` is treated as legacy version `0` by consumers.
- Consumers must continue to display a video when its sidecar is missing or
  malformed, using safe defaults, but must not invent a successful post.
- Unknown future versions may be displayed with safe defaults but must not be
  silently rewritten as version 1.

## Identity

- `job_id` is the stable producer-generated identity for a video job.
- `video_file_id` is the Google Drive file identity and is used for download.
- The filename is a display key only; it is not the sole identity of a job.
