# YouTube allowed-channel limiting — device test matrix

The allowlist is enabled for user testing on this branch. The intended result for a disallowed video is **closed playback**, not a surviving YouTube mini-player.

Record phone model, Android version and YouTube app version for every run.

| Scenario | Expected |
| --- | --- |
| Open a video from an allowed channel | Video remains open and playable |
| Allowed channel → another allowed channel | Playback remains available |
| Allowed channel → disallowed video | Disallowed player closes; no mini-player remains |
| Open a disallowed video from Home | Player closes; no mini-player remains |
| Open a disallowed video from Search | Player closes; no mini-player remains |
| Open a disallowed video from Subscriptions | Player closes; no mini-player remains |
| Disallowed video while a mini-player already exists | Mini-player is dismissed/closed |
| YouTube Shorts with Shorts blocking enabled | Shorts surface is left |
| `Only block during focus` enabled, no focus session | Allowlist does not interfere |
| `Only block during focus` enabled, active focus session | Allowlist is enforced |
| Switch rapidly between channels/videos | No Back-navigation loop or repeated close storm |
| Return to YouTube after a blocked video | Normal navigation remains usable |

Also verify false positives: channel names that are substrings of other names, @handles, long channel names, non-English titles, comments open/closed and playback controls hidden/visible.
