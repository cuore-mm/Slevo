## Why

起動時restoreがlive DB置換後に想定外例外を投げると、汎用例外処理が`DB_SWAPPED`などの回復可能markerを`FAILED`へ上書きする。次回起動がrollback/finalizationを行わず、restore済みDBとrestore前または部分反映DataStoreの不整合を通常状態として起動し得るため、回復可能状態を失わない契約が必要である。

## What Changes

- 起動時の想定外例外を記録する際、回復またはfinalizationを要求する確定済みmarkerを`FAILED`へterminalizeしない。
- live DB置換前だけで安全に失敗確定できる状態と、rollback/finalizationを次回起動へ委ねる状態を明示的に区別する。
- 回復可能状態ではDB/DataStore rollback snapshot、staging、markerを保持し、次回起動が既存のrollbackまたはfinalization経路を必ず再開できるようにする。
- 状態境界ごとの想定外例外を注入し、marker、artifact、次回起動後のDB/DataStore整合性を検証する回帰testを追加する。

## Capabilities

### New Capabilities

- `startup-restore-exception-recovery`: 起動時restoreの想定外例外が発生した場合に、確定済みmarkerの回復意味とrollback/finalization artifactを保持する契約。

### Modified Capabilities

なし。

## Impact

- 主対象: `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
- test対象: `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt`および必要なfake
- marker JSON schema、`RestoreStatus` enum、Room schema、DataStore形式、UI、result表示文言は変更しない。
- 既存のatomic marker publication、durable DB/DataStore rollback snapshot、startup-before-Hilt orderingを前提として利用する。
