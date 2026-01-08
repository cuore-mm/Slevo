# URL Handling (Developer Notes)

This document summarizes the current URL handling paths and the exact parsing rules used in code.
It covers three entry points:
- Deep links (external intents)
- User-entered URLs (URL dialog)
- URLs tapped inside threads

All behavior below reflects the current implementation, not desired behavior.

## Shared helpers (app/src/main/java/com/websarva/wings/android/slevo/ui/util/UrlUtils.kt)

- `parseThreadUrl(url: String): Triple<String, String, String>?`
  - Accepts any host.
  - Matches either:
    - `/test/read.cgi/{board}/{thread}/` (segments[0] == "test" && segments[1] == "read.cgi")
    - `/{board}/dat/{thread}.dat` (segments[1] == "dat")
  - Returns `(host, boardKey, threadKey)` or null.

- `parseBoardUrl(url: String): Pair<String, String>?`
  - Accepts any host.
  - Requires at least one path segment.
  - Returns `(host, boardKey)` using the first path segment.

- `parseItestUrl(url: String): ItestUrlInfo?`
  - Only matches host `itest.5ch.net` or `itest.bbspink.com`.
  - If path contains `read.cgi`, returns board/thread from the segments after it.
  - If path starts with `subback/{board}`, returns board only.
  - Otherwise uses the first path segment as board (threadKey = null).

## Deep link handling

Entry point:
- `DeepLinkHandler` (`app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`)

Flow (functions in `app/src/main/java/com/websarva/wings/android/slevo/ui/util/DeepLinkUtils.kt`):
1. `normalizeDeepLinkUrl(url)`
   - If scheme is `http`, rewrites to `https` (keeps host/path/query/fragment).
2. `parseDeepLinkTarget(url)`
   - Rejects if host is missing or not in allowed suffixes:
     - `bbspink.com`, `5ch.net`, `2ch.sc` (suffix match).
   - Rejects dat format for deep links:
     - `/{board}/dat/{thread}.dat` (checked by `isDatThreadUrl`).
   - Resolution order:
     1) `parseItestUrl`
     2) `parseThreadUrl`
     3) `parseBoardUrl`
3. Navigation:
   - Itest:
     - `TabsViewModel.resolveBoardHost(boardKey)`
     - If success: open board or thread.
     - If fail: show toast (`R.string.invalid_url`).
   - Thread / Board:
     - Directly open with `navigateToThread` / `navigateToBoard`.

Deep link error UX:
- Any parse failure or unsupported URL => toast (`R.string.invalid_url`).

Deep link URL patterns (accepted):
- Board:
  - `https://{host}/{board}/` (allowed host suffixes only)
- Thread:
  - `https://{host}/test/read.cgi/{board}/{thread}/` (allowed host suffixes only)
- Itest:
  - `https://itest.5ch.net/{board}/`
  - `https://itest.5ch.net/test/read.cgi/{board}/{thread}`
  - `https://itest.5ch.net/subback/{board}`

Deep link URL patterns (rejected):
- Dat threads:
  - `https://{host}/{board}/dat/{thread}.dat` (explicitly rejected)

## User-entered URL handling

Entry point:
- `TabScreenContent` URL dialog (`app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabScreenContent.kt`)

Flow (inside `UrlOpenDialog.onOpen`):
1. `parseItestUrl(url)`
   - If matched:
     - Resolve host via `TabsViewModel.resolveBoardHost`.
     - On success, open board or thread.
     - On failure, show error in dialog (`urlError`).
2. `parseThreadUrl(url)`
   - If matched: open thread.
3. `parseBoardUrl(url)`
   - If matched: open board.
4. Otherwise: show error in dialog (`urlError`).

URL input error UX:
- Errors are shown inside the URL dialog (no toast).

URL input URL patterns (accepted):
- Itest (see helpers above).
- Thread:
  - `https://{host}/test/read.cgi/{board}/{thread}/`
  - `https://{host}/{board}/dat/{thread}.dat`
- Board:
  - `https://{host}/{board}/`

URL input URL patterns (treated as board):
- Oyster format is not parsed as thread, so it falls through to `parseBoardUrl`:
  - `https://{host}/{board}/oyster/{prefix}/{thread}.dat`

URL input scheme handling:
- There is no explicit http->https normalization here.
- Navigation always constructs `boardUrl` as `https://{host}/{board}/`.

## In-thread URL handling

Entry point:
- `PostItemBody` tap handler (`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/PostItemBody.kt`)

Flow:
1. `parseThreadUrl(url)`
   - If matched: navigate to thread in-app.
2. Otherwise:
   - `ThreadScreen` uses `LocalUriHandler.openUri(url)` (external browser).

In-thread URL patterns (in-app thread navigation):
- `https://{host}/test/read.cgi/{board}/{thread}/`
- `https://{host}/{board}/dat/{thread}.dat`

In-thread URL patterns (external browser):
- Anything not matching `parseThreadUrl`, including oyster URLs and most itest URLs.
