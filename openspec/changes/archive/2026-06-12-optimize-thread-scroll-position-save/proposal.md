## Why

スレッドタブのスクロール位置保存は、現在 `observeOpenThreadTabs().first()` で一覧全体を読み、対象タブだけを `map` で更新してから `saveOpenThreadTabs()` で一覧全体を保存し直している。保存頻度は `sample(200ms)` で抑制されているが、スクロール位置だけの更新がタブ一覧全体の再保存に依存しており、将来的な並行更新や不要な書き込みのリスクを小さくする余地がある。

## What Changes

- スレッドタブのスクロール位置保存専用の Repository / DAO 更新メソッドを追加する。
- 指定 `threadId` の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` だけを更新し、タブ一覧全体の read-map-save 経路を通さないようにする。
- `ThreadTabCoordinator.updateThreadScrollPosition` は、スクロール位置更新時に専用メソッドを呼び出す構成へ変更する。
- タブの追加、削除、並び替え、pin 状態変更など、タブ一覧そのものを保存する処理は既存の `saveOpenThreadTabs()` 経路を維持する。
- DB スキーマと保存済みデータ形式は変更しない。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-state-sync`: 開いているスレッドタブのスクロール位置を、タブ一覧全体の再保存ではなく対象タブのスクロール列だけを更新する経路で保存する要件を追加する。

## Impact

- 影響範囲:
  - `ThreadTabCoordinator.updateThreadScrollPosition` の保存経路
  - `TabsRepository` のスレッドタブスクロール位置更新 API
  - `OpenThreadTabDao` / `open_thread_tabs` の scroll column 更新クエリ
  - Repository / Coordinator / DAO 周辺のテスト
- 外部 API、DB スキーマ、既存の保存済みデータ形式は変更しない。
- タブ一覧全体の保存処理は維持し、スクロール位置だけの高頻度更新経路を分離する。
