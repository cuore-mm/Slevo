# URL処理の現状まとめ（開発者向け）

このドキュメントは、現在のURL処理フローと判定ルールを開発者向けに整理したものです。
対象は以下の3つの入口です。
- Deep Link（外部インテント）
- ユーザー入力URL（URL入力ダイアログ）
- スレ内リンクのタップ

ここに記載した挙動は「2026/01/10 時点の実装」を示します。

## 共通リゾルバ（app/src/main/java/com/websarva/wings/android/slevo/ui/util/UrlRoutingResolver.kt）

- `resolveUrl(rawUrl: String): ResolvedUrl`
  - `docs/external/5ch.md` の入力URLパターン A〜D を解析する。
  - dat / oyster 形式は `ResolvedUrl.Unknown` として扱う。

- `ResolvedUrl` の種別
  - `ResolvedUrl.Board`
    - PC版板URL
    - `host`（例: `agree.5ch.net`）と `boardKey` を保持
  - `ResolvedUrl.ItestBoard`
    - itest板URL（`subback`）
    - `boardKey` のみ保持（`host` は未解決）
  - `ResolvedUrl.Thread`
    - PC版スレURL / itestスレURL
    - `host` / `boardKey` / `threadKey` を保持
    - itestスレURLは `/<server>/test/read.cgi/...` から `server` を抽出し、
      `itest.{domain}` のサフィックスと結合して `host` を構築する
  - `ResolvedUrl.Unknown`
    - 非対応または解析不能なURL

## Deep Link の処理

エントリポイント:
- `DeepLinkHandler`（`app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`）

フロー（`app/src/main/java/com/websarva/wings/android/slevo/ui/util/DeepLinkUtils.kt` 内）:
1. `resolveDeepLinkUrl(url)`
   - `resolveUrl` の結果が `Unknown` の場合は拒否
   - 許可サフィックス: `bbspink.com`, `5ch.net`, `2ch.sc`（サフィックス一致）
2. 遷移:
   - `ResolvedUrl.ItestBoard`
     - `TabsViewModel.resolveBoardHost(boardKey)` でホスト解決
     - 成功: 板を開く
     - 失敗: トースト表示（`R.string.invalid_url`）
   - `ResolvedUrl.Thread`
     - そのままスレ遷移
   - `ResolvedUrl.Board`
     - そのまま板遷移

Deep Link のエラー表示:
- 解析失敗／対象外URLはトースト（`R.string.invalid_url`）。

Deep Link 対応URL（受け付ける）:
- 板:
  - `http(s)://{host}/{board}/`（許可サフィックスのみ）
- スレ:
  - `http(s)://{host}/test/read.cgi/{board}/{thread}/`（許可サフィックスのみ）
- itest板:
  - `http(s)://itest.{domain}/subback/{board}`
- itestスレ:
  - `http(s)://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/`

Deep Link 対応外URL（拒否）:
- dat / oyster 形式（`ResolvedUrl.Unknown`）

## ユーザー入力URLの処理

エントリポイント:
- `TabScreenContent` URL入力ダイアログ（`app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabScreenContent.kt`）
- `BbsRouteScaffold` URL入力ダイアログ（`app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`）

フロー（`UrlOpenDialog.onOpen` 内）:
1. `resolveUrl(url)` を実行
2. 種別ごとに処理:
   - `ResolvedUrl.ItestBoard`
     - `TabsViewModel.resolveBoardHost` でホスト解決
     - 成功: 板を開く
     - 失敗: ダイアログ内エラー表示
   - `ResolvedUrl.Thread`
     - スレを開く
   - `ResolvedUrl.Board`
     - 板を開く
   - `ResolvedUrl.Unknown`
     - ダイアログ内エラー表示

URL入力のエラー表示:
- ダイアログ内にエラーを表示（トーストは出さない）。

URL入力の対応URL（受け付ける）:
- PC版板URL
  - `http(s)://{server}.{domain}/{board}/`
- PC版スレURL
  - `http(s)://{server}.{domain}/test/read.cgi/{board}/{thread}/`
- itest版板URL（subback）
  - `http(s)://itest.{domain}/subback/{board}`
- itest版スレURL
  - `http(s)://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/`

URL入力の対応外URL:
- dat / oyster 形式（`ResolvedUrl.Unknown`）
- 上記パターン外のURL

URL入力のスキーム取り扱い:
- 正規化は行わず、解析に成功した場合は `https` の `boardUrl` を組み立てて遷移する。

## スレ内リンクの処理

エントリポイント:
- `PostItemBody` のタップ処理（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/PostItemBody.kt`）

フロー:
1. `resolveUrl(url)` を実行
2. `ResolvedUrl.Thread` の場合のみアプリ内でスレ遷移
3. それ以外は `LocalUriHandler.openUri(url)` で外部ブラウザに委譲

スレ内リンクの対応URL（アプリ内遷移）:
- `http(s)://{host}/test/read.cgi/{board}/{thread}/`
- `http(s)://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/`

スレ内リンクの対応外URL（外部ブラウザ）:
- dat / oyster を含む `ResolvedUrl.Unknown`
- 上記以外のURL
