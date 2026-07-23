## 1. 事前確認

- [x] 1.1 `PendingRestoreApplier.kt` の `RealPendingRestoreDataStoreReflector.reflect()` を確認し、parse/pre-validation 後に settings → tabs → cookies の順で DataStore write していることを把握する。
- [x] 1.2 `PendingRestoreDataStoreWriter.kt` の `writeSettings()`、`writeTabs()`、`writePreparedCookies()` が `SlevoPreferenceDataStores` 経由で DataStore を取得していることを確認する。
- [x] 1.3 `SlevoPreferenceDataStores.kt` の settings/tabs/cookies provider と key 定義を確認し、snapshot/rollback で同 provider を使えることを確認する。

## 2. Snapshot / rollback API 設計実装

- [x] 2.1 `PendingRestoreDataStoreWriter.kt` に `DataStoreSnapshot` data class または sealed/data holder を追加し、settings/tabs と optional cookies の `Preferences` snapshot を保持できるようにする。
- [x] 2.2 `PendingRestoreDataStoreWriter.snapshotDataStores(includeCookies: Boolean)` を追加し、settings/tabs は常に snapshot、cookies は `includeCookies == true` の場合のみ snapshot する。
- [x] 2.3 `PendingRestoreDataStoreWriter.restoreDataStores(...)` を追加し、指定された settings/tabs/cookies のみ snapshot へ full overwrite で戻せるようにする。
- [x] 2.4 snapshot restore helper は `prefs.clear()` 後に `Preferences.asMap()` の key/value を戻し、snapshot に存在しない key が残らないことを実装で保証する。
- [x] 2.5 `PreferenceDataStoreFactory.create(...)` を新規に呼ばず、既存の `SlevoPreferenceDataStores.settings/tabs/cookies(context)` のみを使うことを確認する。

## 3. Reflector write phase rollback 実装

- [x] 3.1 `RealPendingRestoreDataStoreReflector.reflect()` で settings/tabs/cookies parse と Cookie pre-validation が成功した後、DataStore write 前に `snapshotDataStores(includeCookies = preparedCookies != null)` を呼ぶ。
- [x] 3.2 `settingsWritten`、`tabsWritten`、`cookiesWritten` flag を追加し、各 write 成功後にのみ `true` にする。
- [x] 3.3 DataStore write phase を `try/catch` で囲み、例外発生時に write 済み flag に基づいて `restoreDataStores(...)` を呼ぶ。
- [x] 3.4 rollback 中の例外は catch して log し、元の DataStore write failure を返す処理を維持する。
- [x] 3.5 `includeCookies=false` または staged `cookies.json` 不在の場合、cookies snapshot/rollback が実行されないことを code path で確認する。
- [x] 3.6 `PendingRestoreApplier.rollbackAndFail(...)`、marker/result file、DB rollback flow は変更しないことを確認する。

## 4. Writer unit test

- [x] 4.1 `PendingRestoreDataStoreWriterTest.kt` または dedicated test に、snapshot restore が snapshot に含まれる key/value だけを残す test を追加する。
- [x] 4.2 String / Boolean / Float / Int / StringSet の key/value を含む Preferences snapshot が restore できることを検証する。
- [x] 4.3 `includeCookies=false` で `snapshotDataStores()` を呼んだ場合、cookies snapshot が `null` または未取得であることを検証する。
- [x] 4.4 rollback 対象 flag が false の store は restore されないことを fake または helper test で確認する。

## 5. Reflector / Applier unit test

- [x] 5.1 `PendingRestoreApplierTest.kt` または fake collaborator test に、settings write 成功後 tabs write failure で settings rollback が呼ばれる test を追加する。
- [x] 5.2 settings/tabs write 成功後 cookies write failure で settings/tabs rollback が呼ばれ、cookies rollback は呼ばれない test を追加する。
- [x] 5.3 settings write failure の場合、settings/tabs/cookies rollback が呼ばれない test を追加する。
- [x] 5.4 rollback failure が発生しても reflector が元の DataStore write failure を返す test を追加する。
- [x] 5.5 parse failure または Cookie pre-validation failure では snapshot/rollback/write が呼ばれない test を追加する。
- [x] 5.6 DataStore write がすべて成功した場合、rollback が呼ばれず既存 success flow が維持される test を確認または追加する。

## 6. ドキュメント・コメント確認

- [x] 6.1 新規 class / data class / interface には AGENTS.md の comment rule に従って KDoc を付ける。
- [x] 6.2 `Preferences.asMap()` から `MutablePreferences` へ戻す helper に unchecked cast が必要な場合、その helper に処理内容と制約をコメントで説明する。
- [x] 6.3 `RealPendingRestoreDataStoreReflector.reflect()` が 30 行を超える、またはさらに長くなる場合は section comment を維持・追加する。

## 7. 検証

- [x] 7.1 `openspec validate rollback-datastore-on-restore-write-failure --strict` を実行し、change spec が valid であることを確認する。
- [x] 7.2 変更後、GitHub Actions `Android CI` を branch `feature/add-backup` で実行し、unit test と build が成功することを確認する。
- [x] 7.3 Codex review を再実行する場合は `codex review --base origin/develop` を使い、DataStore rollback issue が解消されていることを確認する。
