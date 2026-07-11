# QUEUE

Working queue for the KILLER-mode audit loop. Categories rotate: UI, UX, Code,
Data, Old feature, New feature — never the same category twice in a row.

## In progress
(updated per round)

## Found while auditing something else (queued for a future round)
- `reminders_channel_name` = "Rappels Métabolisme" — this is the Android
  notification-channel display name for ALL reminders (meal/water/weight),
  which now live in Journal, not the Métabolisme module. Renaming
  Biolism→Métabolisme kept a pre-existing mislabel instead of fixing it.
  Should read something generic like "Rappels" (channel name doesn't need
  a module prefix at all). NotificationHelper.kt:21.

## Stuck
(3-strike failures land here, per skill's FAILURE & RETRY rule)

## Done
(see LOG.md for the decision trail; see git log for the actual diffs)
