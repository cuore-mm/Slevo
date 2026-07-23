## 1. 共有resource policy（完了済み）

- [x] 1.1 `BackupResourceLimits.kt`にmanifest 64 KiB、DB 256 MiB、settings 1 MiB、tabs 64 KiB、cookies 8 MiB、total 272 MiB、entry count 7のimmutable default policyを追加する。
- [x] 1.2 `limitForEntry(name)`で全既知file entryをmappingし、unknown entryは`null`として呼び出し側へ明示する。
- [x] 1.3 entry/total/count超過の診断値を保持する`BackupResourceLimitExceededException`を追加する。
- [x] 1.4 `BackupModule`からproduction default policyをHiltへ提供し、`BackupResourceLimitsTest`でdefault、custom、known/unknown mappingを検証する。

## 2. Restore bounded read core（完了済み）

- [x] 2.1 `BackupReader`へdefault付き`BackupResourceLimits`を注入し、testからcustom policyを渡せるようにする。
- [x] 2.2 DB entryをheapへ保持せず、固定bufferと実測`Long` counterでtemp fileへbounded streamingする。
- [x] 2.3 JSON entryの無制限`readBytes()`をbounded memory readへ置き換える。
- [x] 2.4 entry別・合計size超過を`BackupRestoreResult.Invalid`へ変換し、超過byteをoutputへ書かない。
- [x] 2.5 file/directoryを含むentry数を計測し、上限超過entryのpayload展開前に拒否する。
- [x] 2.6 DB temp output streamを`use`でcloseし、ZIP read中の`CancellationException`ではpartial temp fileを削除してrethrowする。
- [x] 2.7 既存path、duplicate、required entry、manifest/JSON/DB validationを維持する。

## 3. Export resource enforcement core（完了済み）

- [x] 3.1 `BackupZipWriter`へdefault付き`BackupResourceLimits`を追加し、`BackupRepositoryImpl`からHilt提供済みpolicyを渡す。
- [x] 3.2 known entryへ個別size上限を適用し、unknown entryを含む全emit entryへcount/total上限を適用する。
- [x] 3.3 `writeFileEntry`で`File.length()`を早期判定に使い、streaming中も実際のbyte数とtotalを計測する。
- [x] 3.4 resource limit例外を既存repository error pathから`BackupExportResult.Failure`へ変換し、成功扱いしない。

## 4. 現在までの回帰テスト（完了済み）

- [x] 4.1 production default値、custom policy、known/unknown entry mappingを検証する。
- [x] 4.2 DB entryの個別上限ちょうどと上限超過をrestore/export双方で検証する。
- [x] 4.3 file/directoryを含むrestore entry countの上限ちょうどと上限超過を検証する。
- [x] 4.4 export entry countの上限ちょうどと上限超過を検証する。
- [x] 4.5 SAF outputが`"wt"`を1回だけ要求し、null open後にblockを実行しないことを検証する。
- [x] 4.6 既存`BackupReaderTest`、`BackupZipWriterTest`、backup関連testsを回帰実行する。

## 5. 現在の検証記録

- [x] 5.1 commit `86a62011`をpushし、同じhead SHAのAndroid CI Run #29220274306でunit testsとCI APK buildが成功した。
- [x] 5.2 working treeがcleanで、実装diffにCodex P2のWAL rollback修正を混在させていないことを確認した。

## 6. 実装後監査フォローアップ（未完了）

- [x] 6.1 `BackupReader.copyWithLimit`と`BackupZipWriter.copyWithLimit`が各loopで`min(bufferSize, remaining + 1)`以下だけをreadするよう整理する。完了条件: 上限超過検出用1 byteを超えてinputから先読みせず、entry/total上限ちょうどを許可し、+1 byteを拒否する。
- [x] 6.2 temp DB生成からsuccess previewへのownership transferまでを単一の`try/finally`または同等の一元cleanup境界で管理する。完了条件: limit、I/O、malformed ZIP、manifest/JSON/DB validation、ZIP read中およびDB validator中のcancellationでpartial temp DBを削除し、cancellationはcleanup後にrethrowする。
- [x] 6.3 `readBackup_tempFileCleanup_afterIOException`を実際にtemp output writeが途中で`IOException`を投げるtestへ置換する。完了条件:結果は成功でなく、partial temp DBとstreamがcleanupされる。現在のsuccess ownership testが必要なら正しい名前で分離する。
- [x] 6.4 highly-compressible zero-filled entryを持つ小さいZIP fixtureを追加する。完了条件: compressed/header sizeではなく実展開byte数でentryまたはtotal limit超過を`Invalid`として拒否する。
- [x] 6.5 manifest、settings、tabs、cookiesそれぞれについて個別上限ちょうどと上限+1 byteをcustom policyで検証する。完了条件: 対象entryより前のentryで失敗せず、+1 byteの対象名がdiagnostic detailへ含まれる。
- [x] 6.6 valid manifest/settings/tabs/DBを使い、restore total上限ちょうどで`Success`、上限+1 byteで`Invalid`となるtestへ置換する。完了条件: JSON validation失敗を利用して境界合格を推測しない。
- [x] 6.7 export file sourceの事前`File.length()`より実stream byte数が増えるtest seamまたはcontrolled fixtureを追加する。完了条件: per-entryまたはtotal超過byteをZIPへ書かず、writerを成功扱いしない。
- [x] 6.8 repository levelでresource limit例外が`BackupExportResult.Failure`となるtestを追加する。完了条件: success result/UI eventへ変換されない。
- [x] 6.9 export→restore interoperability testをvalid manifest/settings/tabs/DBで作り直す。完了条件:同じcustom policy内でexport成功したZIPを`BackupReader`がresource limitおよびvalidationの両方で受理する。
- [x] 6.10 `BackupZipWriterTest.tinyLimits`等の未使用helperを削除し、test名とassertionを実際の検証対象へ一致させる。

## 7. 最終検証（未完了）

- [x] 7.1 `openspec validate limit-backup-decompressed-size --strict`と`git diff --check`を実行する。
- [ ] 7.2 6.1–6.10をcommit/push後、記録した`git rev-parse HEAD`と同じ`headSha`でAndroid CIを実行する。完了条件:対象security tests、全unit tests、CI APK buildが成功する。
- [x] 7.3 最終diffを確認する。完了条件:shared policy、bounded restore/export、cleanup、関連testsだけであり、ZIP schema、Room/DataStore、UI、dependency、Codex P2 WAL修正を含まない。
