## Context

`support-5ch-io` では、`5ch.net` の板/スレを設定オン時に `5ch.io` として開くため、`NavigationExtensions.navigateToBoard` / `navigateToThread` 内で `TabsViewModel` の設定キャッシュを参照して route を正規化している。

この構成では、ナビゲーション拡張関数は同期処理である一方、設定値は DataStore 由来の非同期Flowで読み込まれる。起動直後に `TabsViewModel` がまだ設定値を受け取っていない場合、`null` を `false` 扱いするとデフォルトオンの正規化を落とし、`true` 扱いすると過去に設定オフにしたユーザーへ誤変換する。どちらの倒し方も、永続化済み設定値を確定せずに route を正規化している点が根本原因である。

## Goals / Non-Goals

**Goals:**
- 板/スレ route 正規化時に `SettingsRepository` の現在値取得APIを使い、永続化済み設定値に基づいて判定する。
- `NavigationExtensions` から設定参照と `5ch.net` → `5ch.io` 正規化を外し、正規化済み route のタブ保証と画面遷移だけを担わせる。
- 起動直後でも、未設定デフォルトオンの場合は `5ch.io` へ正規化し、過去に設定オフ済みの場合は `5ch.net` のまま開く。
- 影響する全入口を明示し、各入口で正規化済み route がタブ保存と画面遷移の両方に使われるようにする。

**Non-Goals:**
- 投稿処理、スレ立て処理、OkHttpクライアント全体でのURL変換は行わない。
- ブックマーク、履歴、板DB、既存タブの保存済みURLを一括移行しない。
- `threadTitle` をURL正規化対象にしない。
- `5ch.io` / `5ch.net` のBBSMenu URL選択方針、itest host補完メニュー選択方針は変更しない。
- Deep Link許可ドメインやURL解析パターンそのものは変更しない。

## Decisions

### 1. route正規化は `TabsViewModel` の suspend API に集約する

`TabsViewModel` に、板/スレ route を正規化する suspend API を追加する。

- `normalizeBoardRouteForNavigation(route: AppRoute.Board): AppRoute.Board`
- `normalizeThreadRouteForNavigation(route: AppRoute.Thread): AppRoute.Thread`

これらのAPIは `SettingsRepository.getIsRedirect5chNetToIoEnabled()` を呼び、永続化済み設定値を取得してから `normalizeBoardUrlTo5chIo(...)` を適用する。戻り値は正規化不要なら元route、必要なら `boardUrl` だけを置き換えたrouteとする。

代替案として、`NavigationExtensions` を suspend 化する案がある。しかし `NavHostController` の拡張関数を suspend にすると、全呼び出し側の関数シグネチャとUIイベント処理への影響が大きく、ナビゲーション関数が設定取得責務を持ち続ける。正規化をViewModelのユースケースに置く方が、状態取得とUIイベント処理の境界が明確になる。

### 2. `NavigationExtensions` は正規化済みrouteのみ扱う

`navigateToBoard` / `navigateToThread` から以下を削除する。

- `BoardUrlNormalizationInput` / `normalizeBoardUrlTo5chIo` の参照
- `TabsViewModel.isRedirect5chNetToIoEnabled()` による同期キャッシュ参照
- route copy による `boardUrl` 正規化

拡張関数は受け取ったrouteをそのまま使い、タブ保証、current page更新、`NavHostController.navigate` のみを行う。これにより、同期関数内で非同期設定値を推測する状態をなくす。

### 3. 正規化の呼び出しは「route生成後・navigate前」に行う

各入口は、`AppRoute.Board` / `AppRoute.Thread` を生成した直後、`navigateToBoard` / `navigateToThread` を呼ぶ直前に `TabsViewModel` の suspend 正規化APIを呼ぶ。

対象入口と期待する変更は以下の通り。

| 入口 | 代表ファイル | 変更内容 |
|---|---|---|
| URL入力 | `ui/tabs/TabScreenContent.kt` | URL解析後に生成した板/スレrouteを coroutine 内で正規化してから遷移する |
| Deep Link | `ui/navigation/DeepLinkHandler.kt` | 起動直後でも永続化済み設定値を取得してから正規化し遷移する |
| 画面内URL入力 | `ui/bbsroute/BbsRouteScaffold.kt` | URL入力ダイアログから生成したrouteを正規化してから遷移する |
| 板/スレ一覧 | `ui/board/screen/BoardScaffold.kt` | スレ一覧項目から生成したスレrouteを正規化してから遷移する |
| ブックマーク | `ui/bookmarklist/BookmarkListScaffold.kt` | 保存済みURLは変更せず、開くrouteだけ正規化する |
| 履歴 | `ui/history/HistoryListScaffold.kt` | 保存済み履歴は変更せず、開くrouteだけ正規化する |
| 既存スレタブ一覧 | `ui/tabs/OpenThreadsList.kt` | タブに保存されたrouteを開く直前に正規化する |
| レス本文リンク | `ui/thread/res/PostItemBody.kt` | スレURLリンクから生成したrouteを正規化してから内部遷移する |
| ポップアップ/スレ画面内リンク | `ui/thread/screen/ThreadScaffold.kt`, `ui/thread/screen/ThreadScreen.kt` | コールバックで受け取ったrouteを正規化してから遷移する |
| スレ情報シート | `ui/thread/sheet/ThreadInfoBottomSheet.kt` または呼び出し元 | シート内の板/スレ遷移routeを正規化してから遷移する |

### 4. route正規化後のrouteをタブ保証と画面遷移に共用する

各入口では、正規化済みrouteを一度だけ作成し、その同じオブジェクトを `navigateToBoard` / `navigateToThread` に渡す。`NavigationExtensions` はそのrouteで `ensureBoardTab` / `ensureThreadTab` と `navigate` を行うため、タブ保存URLと実際に開く画面URLが一致する。

### 5. 既存の設定監視キャッシュは表示/軽量参照用途に限定する

`TabsViewModel` の設定Flow監視は、UI表示や低リスクな状態表示に必要であれば残してよい。ただし、route正規化の判定では使用しない。route正規化は必ず `SettingsRepository.getIsRedirect5chNetToIoEnabled()` の結果を使う。

## Risks / Trade-offs

- [Risk] 呼び出し入口が多く、正規化APIの呼び忘れが起こる → 影響範囲表にある全ファイルをタスク化し、`navigateToBoard` / `navigateToThread` 呼び出し箇所を検索して確認する。
- [Risk] UIイベントハンドラ内で suspend API を呼ぶため coroutine 起動箇所が増える → 既存の `rememberCoroutineScope` / `LaunchedEffect` / ViewModel callback のいずれか既存パターンに合わせ、UIスレッドをブロックしない。
- [Risk] 正規化済みrouteと未正規化routeが混在するとタブ保存と画面遷移がズレる → 正規化済みrouteをローカル変数として保持し、その同一routeを遷移関数へ渡す実装ルールにする。
- [Risk] DataStore現在値取得が入口ごとに発生する → URL/タブ遷移時のみの低頻度処理であり、ネットワークやメニュー参照より小さいコストである。必要になればViewModel内部で現在値取得APIをラップし、テストで差し替えやすくする。

## Migration Plan

1. `TabsViewModel` に板/スレ route 正規化用の suspend API を追加する。
2. `NavigationExtensions` から設定参照と正規化処理を削除する。
3. `navigateToBoard` / `navigateToThread` の全呼び出し箇所を洗い出し、各入口で正規化済みrouteを渡すように変更する。
4. 起動直後デフォルトオン、起動直後設定オフ、各入口のタブ保存/画面遷移一致をテストする。
5. CIでビルドとユニットテストを確認する。

Rollback は、`NavigationExtensions` の同期正規化へ戻すのではなく、該当変更の呼び出し側正規化追加を取り消し、route正規化APIを未使用に戻す。問題が発生した場合も、設定キャッシュの `null` を true/false に倒す暫定修正は再導入しない。

## Open Questions

- `ThreadInfoBottomSheet` 内の遷移routeをシート自身で正規化するか、呼び出し元で正規化済みcallbackを渡すかは実装時に既存責務に合わせて決める。
- 既存スレタブ一覧から開く際に、設定オンで `5ch.net` 保存タブを `5ch.io` 別タブとして開く既存方針を維持するため、現在のタブ選択挙動との整合を実装時に確認する。
