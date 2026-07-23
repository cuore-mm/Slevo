## Why

復元前のrollback backupでlive DBの`-wal`コピーが失敗しても処理を継続するため、後続rollbackが古いmain DBだけを復元し、WALにcommit済みの変更を失う可能性がある。またmain DBコピー後、WALコピーまたはDB swap前にprocess deathすると、次回起動がpartial rollback snapshotを完成済みと誤認できる。

## What Changes

- live main DBと存在する非空`-wal`を、分割不能なrollback snapshotとして扱う。
- main DBまたは必須WALのコピーに失敗した場合はsnapshotを未完成のままにし、DB swapを開始しない。
- rollback snapshotの全必須fileがコピーされた後だけcompletion markerを公開し、partial snapshotをrecovery対象から除外する。
- 再生成可能な`-shm`をrollback backup/restoreの必須fileから外す。
- `APPLYING`とrollback snapshot完成後の状態を分離し、process death時に「swap未開始」と「swap開始可能または進行中」を判別する。
- markerへ元live DBの有無を保存し、stale recoveryで元DBを誤って削除しない。
- backup済みWALのrollback restore失敗をrollback失敗として扱い、pending snapshotをmanual recovery用に保持する。
- 旧形式の曖昧な`APPLYING` markerでは破壊的な推測を避け、live DBとrollback filesを保持して失敗を記録する。

## Capabilities

### New Capabilities

- `rollback-wal-snapshot`: live main DBとWALのtransactional rollback snapshot作成、公開、復元、およびprocess-death recoveryを規定する。

### Modified Capabilities

なし。backup/restore capabilityはまだmain specsへarchiveされていないため、本changeでは独立したrollback safety capabilityとして追加する。

## Impact

- `PendingRestoreDbSwapper.kt`: WAL必須コピー、SHM除外、snapshot completion判定、WAL復元failure契約。
- `PendingRestoreApplier.kt`: marker状態遷移、stale `APPLYING`/snapshot-ready recovery。
- `PendingRestoreMarker.kt` / `RestoreStatus`: 元DB有無とrollback-ready phaseの永続化。
- `PendingRestoreDbSwapperTest.kt` / `PendingRestoreApplierTest.kt`: partial snapshot、WAL failure、process-death recoveryの回帰test。
- 既存ZIP schema、backup export、Room schema、DataStore、UI、dependencyは変更しない。
