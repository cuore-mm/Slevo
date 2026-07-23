## 1. `BackupOutputWriter` 回帰テストの準備

- [x] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/export/BackupOutputWriterTest.kt` を追加し、MockK の `Context` と `ContentResolver` から mode ごとに動作を変えられる `OutputStream` test double を返せるようにする。完了条件: production code を変更する前に `writeToUri` が要求した URI と mode を観測できる。
- [x] 1.2 test double と test class に repository の comment/KDoc rules に従った型説明を追加する。完了条件: 新規 class/object の KDoc が annotation より前にあり、非自明な mode 分岐と旧内容から新内容への変換がコメントで説明されている。

## 2. SAF truncate 契約のテスト

- [x] 2.1 `BackupOutputWriterTest.kt` に、対象 URI が `openOutputStream(uri, "wt")` で一度だけ開かれる test を追加する。完了条件: exact mode が `"wt"` であり、`"w"`、`"rw"`、`"rwt"` の呼び出しがないことを検証する。
- [x] 2.2 大きい既存 byte sequence を保持する mode-sensitive test double に小さい新 byte sequence を書き込む test を追加する。完了条件: `"wt"` open 後の最終内容と byte length が新しい sequence のみに一致し、旧末尾が残っていない。
- [x] 2.3 `openOutputStream(uri, "wt")` が `null` を返す場合の test を追加する。完了条件: `BackupOutputException` が発生し、書き込み block が実行されない。
- [x] 2.4 `openOutputStream(uri, "wt")` が例外を投げる場合の test を追加する。完了条件: `BackupOutputWriter` が元の open 例外を変更せず伝播し、同じ URI を `"w"` または別 mode で再度開かない。
- [x] 2.5 block の正常終了時と例外終了時の stream close test を追加または既存 test で確認する。完了条件: 両経路で close が試行され、block 例外を成功扱いしない。block failure と finally close failure の例外優先順位は変更しない。
- [x] 2.6 `BackupZipWriterTest` の underlying output close failure test を回帰実行する。完了条件: ZIP entry 書き込み後に `OutputStream.close()` が失敗した場合も `BackupZipWriter.isSuccessful()` が false となり、export 成功条件を満たさない。

## 3. Production code の最小修正

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/BackupZipWriter.kt` の `BackupOutputWriter.writeToUri` で、`ContentResolver.openOutputStream(uri, "w")` を `ContentResolver.openOutputStream(uri, "wt")` へ変更する。完了条件: production code の SAF output open mode が write-only truncate を明示する。
- [x] 3.2 `BackupOutputWriter.writeToUri` の失敗経路を確認し、`null` だけを既存の `BackupOutputException` へ変換し、open 例外は catch/wrap せず `BackupRepositoryImpl.writeZip` の既存 error handling へ伝播させ、別 mode の fallback を追加しない。完了条件: 2.3 と 2.4 の tests が成功し、repository 境界ではどちらも `BackupExportResult.Failure` になる既存契約が維持される。
- [x] 3.3 production diff を確認し、`BackupZipWriter` の ZIP entry/close/success 判定、`BackupRepositoryImpl`、UI、復元処理、`ParcelFileDescriptor`、`Os.ftruncate`、URI delete/recreate、一時 ZIP、権限、依存関係を変更していないことを確認する。完了条件: production scope が `BackupOutputWriter.writeToUri` の truncate mode 修正に限定されている。

## 4. 検証

- [x] 4.1 `openspec validate fix-saf-backup-truncation --strict` を実行する。完了条件: OpenSpec strict validation が成功する。
- [x] 4.2 implementation と tests を commit/push し、`git rev-parse HEAD` で検証対象 SHA を記録してから、current branch に対して `gh workflow run "Android CI" --ref <current-branch> --repo cuore-mm/Slevo` を実行する。完了条件: workflow run の `headSha` が記録した SHA と一致し、unit tests と CI APK build を含む Android CI が成功する。SHA: `83962d45` / Run #29099128227 PASS
- [x] 4.3 CI が利用できない場合のみ `./gradlew :app:testDebugUnitTest --tests "com.websarva.wings.android.slevo.data.backup.export.BackupOutputWriterTest" --tests "com.websarva.wings.android.slevo.data.backup.export.BackupZipWriterTest"` を実行する。完了条件: 変更対象と回帰 tests が成功する。CI が成功した場合は代替実行不要として完了扱いにする。
- [x] 4.4 `git diff --check` と実装開始時点からの `git diff` を確認する。完了条件: whitespace error がなく、P2-5以外の機能変更が混在していない。

## 5. 実装後監査で判明した test gap の是正

- [x] 5.1 `BackupZipWriter.kt` の `BackupOutputWriter` に、`(Uri, String) -> OutputStream?` 相当の opener を受け取る internal constructor または同等の最小 test seam を追加し、既存の `@Inject` constructor は `context.contentResolver.openOutputStream(uri, mode)` を委譲する。完了条件: Hilt利用側と公開APIを変更せず、testから戻り値・null・固有例外を決定できる。
- [x] 5.2 `BackupOutputWriter.writeToUri` が seam を必ず `"wt"` で1回だけ呼び出すようにし、productionのnull変換、open例外伝播、fallbackなし、finally closeの動作を維持する。完了条件: `"w"`、`"rw"`、`"rwt"` のproduction呼び出しを追加せず、既存動作との差分がtestabilityだけに限定される。
- [x] 5.3 `BackupOutputWriterTest.kt` の `TruncationTestStream` を、初期状態で旧byte sequenceとlogical sizeを実際に保持し、modeに`"t"`がある場合だけsizeを0へ変更するmode-sensitive storage test doubleへ置き換える。完了条件: 小さい新内容を`"wt"`で書いた結果は新内容だけになり、同じdoubleを`"w"`で開く対照testでは新内容の後ろに旧末尾が残る。
- [x] 5.4 `writeToUri_neverFallsBackToWMode` の重複した成功経路testを削除または置換し、openerが`"wt"`で固有の例外instanceを投げるtestを追加する。完了条件: 同じ例外instanceが変更されず呼び出し元へ伝播し、blockは実行されず、openerの呼び出し履歴が`"wt"`の1回だけである。
- [x] 5.5 `BackupOutputWriterTest.kt` から「`ContentResolver.openOutputStream`はfinalなので例外をstubできない」という断定を削除し、test class KDocを実際のcoverage（mode、null、open例外、close）に一致させる。完了条件: commentがtestされていない保証やframework APIの誤った制約を主張しない。
- [x] 5.6 `BackupZipWriter.kt` の `BackupOutputWriter` class-level KDocを`ContentResolver.openOutputStream(uri, "wt")`によるwrite-only truncate出力へ更新する。完了条件: class-levelとmethod-levelの説明が一致し、KDocはannotationより前にある。

## 6. 追加是正後の検証

- [x] 6.1 `openspec validate fix-saf-backup-truncation --strict` と `git diff --check` を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 6.2 implementation/testsをcommit/push後、記録した`git rev-parse HEAD`と同じ`headSha`で`Android CI`を実行する。完了条件: `BackupOutputWriterTest`、既存backup tests、CI APK buildを含むworkflowが成功し、working treeがcleanである。SHA: `a203b540` / Run #29136170658 PASS
- [x] 6.3 最終diffを確認する。完了条件: production変更は`BackupOutputWriter`のtest seam・`"wt"`委譲・KDocに限定され、ZIP format、repository/UI、復元処理、Room/DataStore、権限、dependencyに変更がない。
