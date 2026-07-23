## Why

pending restore の DataStore 反映では、settings と tabs を先に書き込んだ後で cookies の parse / serialize / write を行う。Cookie 復元に失敗すると restore 全体は失敗扱いになり DB は rollback されるが、すでに書き込まれた settings/tabs DataStore は戻らず、失敗した restore の部分適用が残る。

Cookie データ不正など事前に検出できる失敗で DataStore を部分適用しないよう、DataStore mutation 前に cookie 復元データを検証・serialize する計画を追加する。

## What Changes

- `PendingRestoreDataStoreWriter` に Cookie 復元データを DataStore 書き込み前に検証・serialize する pure/helper API を追加する。
- `RealPendingRestoreDataStoreReflector.reflect()` の処理順を、全 JSON parse → Cookie pre-validation → DataStore write の順に変更する。
- Cookie parse / conversion / serialization failure の場合は settings/tabs/cookies DataStore を一切変更せず error を返す。
- 検証済み cookie JSON set を cookies DataStore に書き込む commit API を追加し、既存 `writeCookies` は必要に応じて互換 wrapper とする。
- Cookie pre-validation の success/failure、reflector の write ordering、applier rollback path をテストで保証する。
- DataStore の I/O failure まで cross-DataStore transaction として完全 rollback することは対象外とする。

## Capabilities

### New Capabilities

- `pending-restore-datastore-prevalidation`: pending restore の DataStore 反映前に Cookie 復元データを検証し、事前検出可能な Cookie failure で settings/tabs の部分適用を防ぐ。

### Modified Capabilities

- なし

## Impact

- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriter.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupRestoreMapper.kt` (invalid cookie test target only; behavior changeは本 change の必須範囲外)
- Affected tests:
  - `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriterTest.kt`
  - `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt`
  - `app/src/test/java/com/websarva/wings/android/slevo/data/backup/restore/BackupRestoreMapperTest.kt`
- API impact:
  - public app API / UI は変更しない。
  - pending restore 内部 API のみ追加・整理する。
- Dependencies:
  - 新規 dependency は追加しない。
