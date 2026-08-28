## Why

現在は自分の投稿への返信を確認するには、各スレッドを開いて新着レスを目視する必要がある。アプリ操作中にスレッドを再取得した時点で返信を通知し、タブ一覧からの一括更新とスレッド画面での更新のどちらでも見落としを減らす。

## What Changes

- 確定済みの自分のレスを `>>レス番号` で参照する新着レスを返信として検出する。
- スレッド画面とタブ画面の取得経路を、取得後の返信検出・重複防止を行う共通処理へ接続する。
- 検出済み返信をRoomへ永続化し、画面間、再取得間、アプリ再起動後の重複通知を防止する。
- Androidの通知チャネル、通知権限要求、通知投稿、通知タップによる対象スレッドへの遷移を追加する。
- 一般設定に返信通知の有効・無効を切り替える項目を追加する。
- 初回取得や通知無効中に取得済みの過去レスは遡って通知しない。
- バックグラウンド定期取得、範囲アンカー記法、通知タップ後の対象レス位置へのスクロールは対象外とする。

## Capabilities

### New Capabilities

- `reply-notification`: アプリ操作中のスレッド再取得で自分のレスへの新着返信を一度だけ検出し、Android通知として提示する契約。

### Modified Capabilities

なし。

## Impact

- スレッド取得: `ThreadContentLoadUseCase`、`ThreadRouteViewModel`、`ThreadTabsCoordinator`、取得後処理の新規UseCase
- 自レス・返信解析: `OwnPostReconciliationUseCase`、`PostHistoryRepository`、`ThreadDisplayTransformers`
- 永続化: `AppDatabase`、新規Entity/DAO/Repository、DB version 11から12へのmigration、Room schema export、バックアップ復元時のmigration chain
- Android通知: `AndroidManifest.xml`、`SlevoApplication`、通知Publisher、通知チャネル、PendingIntent、通知用resources
- UI・設定: 一般設定のUiState/ViewModel/Composable、Android 13以降の通知権限要求
- テスト: 返信解析・検出UseCase・重複防止・両取得経路・設定・通知Publisher・Room migration・通知タップ遷移
