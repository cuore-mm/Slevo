## Why

保留復元で既存データベースの `-wal` / `-shm` 削除が `false` を返しても、現在はメイン DB の置換または rollback が続行される。異なる DB 世代の sidecar が新しいメイン DB と共存し、無関係なページの再生、検証失敗、データ損失につながり得るため、置換前条件として削除成功を必須化する。

## What Changes

- 既存 `-wal` / `-shm` の削除結果を観測し、存在していた sidecar を安全に除去できなければ DB 置換を開始しない。
- rollback でも同じ前条件を適用し、sidecar cleanup 失敗時はメイン DB の削除・上書きおよび rollback WAL の復元を開始しない。
- cleanup 失敗を既存の失敗経路へ伝播し、安全な rollback を完了できない場合は `ROLLBACK_REQUIRED` と pending/rollback artifact を保持して次回起動で再試行可能にする。
- sidecar 削除失敗を決定的に注入できるテスト seam と、置換・rollback の非進行および artifact 保持を検証する unit test を追加する。
- UI、marker schema、backup format、rollback snapshot format は変更しない。

## Capabilities

### New Capabilities

- `pending-restore-sidecar-safety`: 保留復元の DB 置換・rollback 前に live WAL/SHM cleanup を完了し、失敗時に破壊的処理を停止して回復可能状態を保持する契約。

### Modified Capabilities

なし。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDbSwapper.kt`
- `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDbSwapperTest.kt`
- `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt`
- 既存の `PendingRestoreApplier` rollback/retry 経路を利用し、公開 API、永続 schema、ファイル format、画面表示は変更しない。
