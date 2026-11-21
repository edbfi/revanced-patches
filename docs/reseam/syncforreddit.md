# Sync for Reddit

## Compatible version

| Package | Compatible version |
| --- | --- |
| `com.laurencedawson.reddit_sync` | `v23.06.30-13:39`, `23033` |

## Patches

| Patch | What it does | Default |
| --- | --- | --- |
| Disable ads | Removes ads and the ad consent form, and keeps the ad SDKs from starting. | On |
| Disable Sync for Lemmy bottom sheet | Prevents the Sync for Lemmy bottom sheet from opening at startup. | On |
| Fix /s/ links | Opens Reddit share links (/s/) as the post they point to, from the app and from other apps. | On |
| Fix galleries | Restores loading Reddit galleries from the post listing. | On |
| Fix Imgur | Loads Imgur albums and covers and uploads images through the Imgur API. | Off |
| Fix Other discussions | Fixes “Other discussions” failing to load, by searching Reddit for the link instead. | On |
| Fix post thumbnails | Fixes post thumbnails not loading. | On |
| Fix Tenor previews | Loads missing Tenor thumbnails from public GIF pages. | On |
| Fix video downloads | Fixes downloaded Reddit videos having only audio. | On |
| Fix View more comments | Fixes “View more” not loading further comments. | On |
| Hide Gfycat link previews | Hides broken Gfycat link previews. | On |
| Use /user/ endpoint | Requests user profiles from /user/ instead of /u/. | Off |

## Options

### Fix Imgur

`Imgur client ID` identifies an Imgur application you are authorized to use. Reseam stores the ID in the
patched APK so the app can request albums and covers. This option has no preset ID.

## Known issues

- **Other discussions can miss posts or show posts with different links.** It uses Reddit search.
- **Some Tenor thumbnails stay blank.** Sync contacts the public GIF page, which must publish a preview image.
