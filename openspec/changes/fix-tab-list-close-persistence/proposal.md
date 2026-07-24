## Why

タブ一覧から最後のスレッドタブを閉じる処理は Composition 所有の coroutine で実行されるため、タブ消失に伴う画面遷移や BottomSheet の破棄で処理がキャンセルされ、DB 削除がロールバックしてタブが再表示される可能性がある。既にスレッド画面のクローズ経路で導入済みの retained close 契約をタブ一覧にも適用し、同じ永続化保証を持たせる必要がある。

## What Changes

- `TabScreenContent` のスレッドタブ close callback を、Composition 所有の `closeThreadTab` 呼び出しから `TabSessionStore.requestCloseThreadTab` への委譲へ変更する。
- 最後のスレッドタブを閉じた後に呼び出し元 Composition が破棄されても、DB 書き込みと Room 正規状態の確認まで retained scope で継続する。
- タブ一覧の callback が retained close API へ委譲することを決定論的に検証する回帰テストを追加する。
- 既存の削除アニメーション、ナビゲーション、FIFO、メタデータ、キャンセル境界、および UI 表示は変更しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tablist-ui`: タブ一覧から開始したスレッドタブ close を、タブ一覧 Composition の破棄に依存せず永続化完了まで継続する要件を追加する。

## Impact

- 対象実装: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenContent.kt`
- 対象テスト: タブ一覧 close callback の委譲を検証する `app/src/test/.../ui/tabs/` 配下の unit test
- 再利用 API: `TabSessionStore.requestCloseThreadTab(threadKey, boardUrl)`
- DB schema、外部 API、依存関係、表示文言、アクセシビリティ構造への変更はない。
