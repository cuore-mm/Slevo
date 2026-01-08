# URL処理の現状まとめ（開発者向け）

このドキュメントは、現在のURL処理フローと判定ルールを開発者向けに整理したものです。
対象は以下の3つの入口です。
- Deep Link（外部インテント）
- ユーザー入力URL（URL入力ダイアログ）
- スレ内リンクのタップ

ここに記載した挙動は「現在の実装」を示します。

## 共通ヘルパー（app/src/main/java/com/websarva/wings/android/slevo/ui/util/UrlUtils.kt）

- `parseThreadUrl(url: String): Triple<String, String, String>?`
  - hostの制限なし。
  - 以下のいずれかに一致した場合にスレと判定。
    - `/test/read.cgi/{board}/{thread}/`（segments[0] == "test" && segments[1] == "read.cgi"）
    - `/{board}/dat/{thread}.dat`（segments[1] == "dat"）
  - 戻り値は `(host, boardKey, threadKey)`。

- `parseBoardUrl(url: String): Pair<String, String>?`
  - hostの制限なし。
  - パスセグメントが1つ以上必要。
  - 先頭セグメントを板キーとして `(host, boardKey)` を返す。

- `parseItestUrl(url: String): ItestUrlInfo?`
  - 対象ホストは `itest.5ch.net` と `itest.bbspink.com` のみ。
  - `read.cgi` が含まれる場合は、後続セグメントから板/スレを抽出。
  - `subback/{board}` の場合は板として扱う。
  - それ以外は先頭セグメントを板として扱い、`threadKey = null`。

## Deep Link の処理

エントリポイント:
- `DeepLinkHandler`（`app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`）

フロー（`app/src/main/java/com/websarva/wings/android/slevo/ui/util/DeepLinkUtils.kt` 内）:
1. `normalizeDeepLinkUrl(url)`
   - scheme が `http` の場合、`https` に書き換える（host/path/query/fragmentは維持）。
2. `parseDeepLinkTarget(url)`
   - host が空、または許可サフィックス外の場合は拒否。
     - 許可サフィックス: `bbspink.com`, `5ch.net`, `2ch.sc`（サフィックス一致）
   - dat 形式はDeep Linkでは拒否。
     - `/{board}/dat/{thread}.dat`（`isDatThreadUrl` で判定）
   - 判定順序:
     1) `parseItestUrl`
     2) `parseThreadUrl`
     3) `parseBoardUrl`
3. 遷移:
   - itest:
     - `TabsViewModel.resolveBoardHost(boardKey)` でホスト解決。
     - 成功: 板またはスレを開く。
     - 失敗: トースト表示（`R.string.invalid_url`）。
   - thread / board:
     - `navigateToThread` / `navigateToBoard` で遷移。

Deep Link のエラー表示:
- 解析失敗／対象外URLはトースト（`R.string.invalid_url`）。

Deep Link 対応URL（受け付ける）:
- 板:
  - `https://{host}/{board}/`（許可サフィックスのみ）
- スレ:
  - `https://{host}/test/read.cgi/{board}/{thread}/`（許可サフィックスのみ）
- itest:
  - `https://itest.5ch.net/{board}/`
  - `https://itest.5ch.net/test/read.cgi/{board}/{thread}`
  - `https://itest.5ch.net/subback/{board}`

Deep Link 対応外URL（拒否）:
- dat 形式:
  - `https://{host}/{board}/dat/{thread}.dat`（明示的に拒否）

## ユーザー入力URLの処理

エントリポイント:
- `TabScreenContent` URL入力ダイアログ（`app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabScreenContent.kt`）

フロー（`UrlOpenDialog.onOpen` 内）:
1. `parseItestUrl(url)`
   - 一致:
     - `TabsViewModel.resolveBoardHost` でホスト解決。
     - 成功: 板またはスレを開く。
     - 失敗: ダイアログ内にエラー表示（`urlError`）。
2. `parseThreadUrl(url)`
   - 一致: スレを開く。
3. `parseBoardUrl(url)`
   - 一致: 板を開く。
4. それ以外: ダイアログ内にエラー表示（`urlError`）。

URL入力のエラー表示:
- ダイアログ内にエラーを表示（トーストは出さない）。

URL入力の対応URL（受け付ける）:
- itest: 上記の `parseItestUrl` と同じ。
- スレ:
  - `https://{host}/test/read.cgi/{board}/{thread}/`
  - `https://{host}/{board}/dat/{thread}.dat`
- 板:
  - `https://{host}/{board}/`

URL入力で板扱いになるURL:
- oyster 形式はスレ判定されないため `parseBoardUrl` に落ちる:
  - `https://{host}/{board}/oyster/{prefix}/{thread}.dat`

URL入力のスキーム取り扱い:
- 明示的な `http -> https` 正規化はなし。
- 遷移に使う `boardUrl` は常に `https://{host}/{board}/` で構築する。

## スレ内リンクの処理

エントリポイント:
- `PostItemBody` のタップ処理（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/PostItemBody.kt`）

フロー:
1. `parseThreadUrl(url)`
   - 一致: アプリ内でスレ遷移。
2. それ以外:
   - `ThreadScreen` の `LocalUriHandler.openUri(url)` で外部ブラウザに委譲。

スレ内リンクの対応URL（アプリ内遷移）:
- `https://{host}/test/read.cgi/{board}/{thread}/`
- `https://{host}/{board}/dat/{thread}.dat`

スレ内リンクの対応外URL（外部ブラウザ）:
- `parseThreadUrl` に一致しないもの（oysterやitestの多くを含む）。
