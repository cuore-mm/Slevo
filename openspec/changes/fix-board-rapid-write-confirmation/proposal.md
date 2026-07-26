## Why

Board タブの同一操作を短時間に繰り返すと、Room が中間状態を通知せず最終状態だけを通知できるため、中間値を待つ command が `pendingCommands` に残り続ける。特に 200ms 間隔のスクロール保存で stale projection と未解放 pending が蓄積するため、既存の targeted persistence を保ったまま最終 intent へ収束させる必要がある。

## What Changes

- Board の同一 `boardUrl`・同一更新種別（scroll、pin、resolved info）では、後続 command が未完了の先行 command を supersede する。
- supersede された command を terminal `NoOp` として解放し、projection と canonical confirmation の対象から除外する。
- 最新 command の write/confirmation failure だけを当該 intent の terminal failure とし、projection を DB canonical state へ戻す。
- Room が中間値を省略して最終値だけを通知しても pending が有界になり、最終 canonical state へ収束する deterministic unit test を追加する。
- Ensure、Delete、Deep Link terminal result、隣接 close、selection、full-list persistence、および Thread の挙動は変更しない。

## Capabilities

### New Capabilities

- `board-rapid-write-convergence`: Board の同一操作に対する rapid targeted write の supersession、terminal result、pending 上限、最終 canonical convergence を定義する。

### Modified Capabilities

なし。

## Impact

- 実装対象: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt`
- テスト対象: `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinatorTest.kt`
- DB schema、DAO、Repository API、UI、navigation、Thread coordinator に変更はない。
