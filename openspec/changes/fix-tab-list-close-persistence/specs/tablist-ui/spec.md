## ADDED Requirements

### Requirement: タブ一覧スレッド close の retained 実行
システムはタブ一覧からスレッドタブの削除アニメーションが完了して close を確定するとき、タブ一覧 Composition より長く存続する `TabSessionStore` の retained scope に close 処理を委譲しなければならないMUST。システムはタブ一覧 Composition 所有の coroutine に repository 削除と Room 正規状態確認の完了を依存させてはならないMUST NOT。既存の削除アニメーション、ナビゲーション、FIFO、メタデータ、および store lifetime でのキャンセル契約を維持しなければならないMUST。

#### Scenario: 最後のスレッドタブ close 後にタブ一覧が破棄される
- **WHEN** ユーザーがタブ一覧から最後のスレッドタブを閉じ、タブ消失に伴うナビゲーションまたは BottomSheet の破棄でタブ一覧 Composition が削除される
- **THEN** システムは `TabSessionStore` の retained scope で DB 削除と Room 正規状態確認を継続し、削除済みタブを再表示しない

#### Scenario: タブ一覧 callback が retained close API へ識別子を渡す
- **WHEN** タブ一覧の閉じるボタン、スワイプ削除、または長押しメニュー削除のアニメーション完了後にスレッドタブ close callback が呼び出される
- **THEN** システムは対象 `ThreadTabInfo` の `threadKey` と `boardUrl` を `TabSessionStore.requestCloseThreadTab` に一度渡し、Composition 所有 scope から suspend close API を実行しない

#### Scenario: store lifetime 境界では close をキャンセルする
- **WHEN** retained close の完了前に `TabSessionStore` 自身の lifetime が終了する
- **THEN** システムは既存どおり store 所有 scope をキャンセルし、それ以外の caller Composition の破棄をキャンセル境界にしない
