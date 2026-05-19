## Why

`5ch.net` を `5ch.io` として開く route 正規化が、同期的なナビゲーション拡張関数内で `TabsViewModel` の一時キャッシュ値に依存している。起動直後に設定値が未読込の場合、デフォルトオンの変換がスキップされたり、過去に設定オフにしたユーザーへ誤変換が発生したりするため、正規化時点で永続化済み設定値を確定してから判定する必要がある。

## What Changes

- route 正規化の責務を `NavigationExtensions` から外し、`TabsViewModel` の suspend API に集約する。
- `SettingsRepository` の現在値取得APIを使い、ナビゲーション直前に永続化済み設定値を取得してから `boardUrl` を正規化する。
- `navigateToBoard` / `navigateToThread` は正規化済み route を受け取り、タブ保証と画面遷移のみを行う薄い関数へ戻す。
- URL入力、Deep Link、既存タブ、ブックマーク、履歴、板一覧、レス本文リンク、スレ情報シートなど、板/スレを開く全入口で事前正規化を呼び出す。
- `threadTitle` は引き続きURL正規化対象外とし、タイトル未取得入口では `null` を維持する。
- `itest.5ch.net/subback/{board}` のhost補完は既存方針どおり、永続化済み設定値と入力元ドメインに基づいて `.net` / `.io` を選択する。

## Capabilities

### New Capabilities
- `navigation-route-normalization`: 永続化済み設定値に基づき、板/スレ route を開く直前に正規化する責務と入口ごとの適用範囲を定義する。

### Modified Capabilities
- `resolve-url-routing`: URL解析後に生成する板/スレ route の正規化タイミングと設定値参照方法を変更する。
- `handle-url-input`: URL入力から板/スレを開く際、同期キャッシュではなく永続化済み設定値で route を正規化する。
- `handle-deep-link`: Deep Linkで板/スレを開く際、起動直後でも永続化済み設定値で route を正規化する。
- `handle-thread-link`: レス本文などのスレリンクを開く際、永続化済み設定値で route を正規化する。
- `bookmark`: ブックマークから板/スレを開く際、保存データを変更せず、開く route のみを永続化済み設定値で正規化する。

## Impact

- 影響を受ける主要実装:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/NavigationExtensions.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabsViewModel.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/repository/SettingsRepository.kt`
- 事前正規化の呼び出し追加が必要な入口:
  - URL入力: `TabScreenContent.kt`
  - Deep Link: `DeepLinkHandler.kt`
  - 画面内URL入力: `BbsRouteScaffold.kt`
  - 板一覧/スレ一覧からスレを開く導線: `BoardScaffold.kt`
  - ブックマーク: `BookmarkListScaffold.kt`
  - 履歴: `HistoryListScaffold.kt`
  - 既存スレタブ: `OpenThreadsList.kt`
  - レス本文リンク/ポップアップ内リンク: `PostItemBody.kt`, `ThreadScaffold.kt`, `ThreadScreen.kt`
  - スレ情報シート内の遷移: `ThreadInfoBottomSheet.kt` または呼び出し元
- 影響を受けない/変更しない領域:
  - 投稿処理、スレ立て処理、OkHttpクライアント全体でのURL変換
  - ブックマーク、履歴、板DB、既存タブの一括移行
  - `threadTitle` のURL正規化
  - `5ch.io` / `5ch.net` BBSMenu URLの選択方針
- テスト影響:
  - 起動直後のデフォルトオンで `5ch.net` が `5ch.io` に正規化されるケース
  - 過去に設定オフ済みの場合、起動直後でも `5ch.net` のまま開くケース
  - 各入口で正規化済み route がタブ保証と画面遷移に使われるケース
