## Context

バックアップ export は `BackupRepositoryImpl.writeZip` から `BackupOutputWriter.writeToUri` へ選択済み SAF `Uri` を渡し、`BackupZipWriter` が返された `OutputStream` に ZIP entry を直接書き込む。現在の `BackupOutputWriter.writeToUri` は `ContentResolver.openOutputStream(uri, "w")` を使用している。

Android の `ContentResolver` / `DocumentsProvider` 契約では `"w"` の truncate 有無は provider ごとに異なる。一方、`ParcelFileDescriptor.parseMode` の `t` は truncate 要求を表すため、write-only truncate は `"wt"` で明示する必要がある。`CreateDocument` から既存ドキュメントの URI が返り、新しい ZIP が以前より小さい場合、`"w"` のままでは新しい ZIP の終端以降に旧データが残る可能性がある。

現在のデータフローは次のとおりである。

```text
BackupScreen(CreateDocument)
  -> BackupViewModel.onUriReceived
  -> BackupRepositoryImpl.exportBackup
  -> BackupRepositoryImpl.writeZip
  -> BackupOutputWriter.writeToUri
  -> ContentResolver.openOutputStream(uri, "w")
  -> BackupZipWriter(OutputStream)
```

本変更後もデータフローと直接 streaming は維持し、保存先を開く mode だけを truncate 明示へ変更する。

## Goals / Non-Goals

**Goals:**

- 既存 SAF ドキュメントへのバックアップ出力開始時に write-only truncate を明示する。
- truncate 対応 mode で保存先を開けない場合、破損リスクのある非 truncate 書き込みへフォールバックせず export を失敗させる。
- 既存ファイルより小さい内容を上書きする回帰ケースと、指定 mode を自動テストで固定する。
- 既存の成功判定、例外伝播、stream close、ユーザー向け共通失敗表示を維持する。

**Non-Goals:**

- SAF provider 自体が `"wt"` 契約に違反する場合までアプリ側で補正すること。
- URI の delete/recreate、rename、atomic replace を実装すること。
- `ParcelFileDescriptor`、seek、`android.system.Os.ftruncate` を導入すること。
- ZIP 全体を新しい一時ファイルへ生成してから SAF URI へコピーすること。
- ZIP format、manifest、バックアップ内容、復元処理、UI、Room/DataStore schema を変更すること。
- ZIP 書き込み途中に失敗した出力先の cleanup 契約を変更すること。

## Decisions

### 1. `openOutputStream(uri, "wt")` を使用する

`app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/BackupZipWriter.kt` の `BackupOutputWriter.writeToUri` は、`ContentResolver.openOutputStream(uri, "w")` を `ContentResolver.openOutputStream(uri, "wt")` へ変更する。`w` は write-only、`t` は既存 regular file の長さを 0 にする要求であり、この問題に必要な最小権限と挙動を表す。

`"rwt"` は採用しない。read 権限は export に不要であり、Android の provider 契約上、`rw` / `rwt` のような複合 mode は seek 可能な通常ファイルを前提とする。pipe または cloud-backed stream を提供する実装との互換性を不要に狭めるためである。

### 2. `"wt"` 失敗時に `"w"` へフォールバックしない

provider が `"wt"` を拒否して `UnsupportedOperationException`、`FileNotFoundException`、`SecurityException`、その他の open 失敗を返した場合、`BackupOutputWriter` は open 例外を catch/wrap せずそのまま呼び出し元へ伝播する。`openOutputStream` が `null` の場合だけ、既存どおり `BackupOutputException` を生成する。`BackupRepositoryImpl.writeZip` はどちらも既存の exception handling で捕捉し、`BackupExportResult.Failure` へ変換する。

`"w"` への再試行は既知の末尾残存リスクを復活させ、バックアップを成功扱いできないため禁止する。UI は既存どおり共通の失敗 Snackbar を表示し、詳細はログへ記録する。

### 3. ZIP は現在の直接 streaming を維持する

`BackupRepositoryImpl.writeZip` と `BackupZipWriter` の entry 書き込み順、close、成功判定は変更しない。一時 ZIP を追加しても最終的な SAF copy が `"w"` なら truncate 問題は解消せず、`"wt"` と組み合わせる場合も cache 使用量と I/O が増えるだけで本修正には不要である。

`delete(uri)` 後の再作成も採用しない。削除成功後に同じ URI が有効である保証がなく、ユーザーが選択したドキュメントを失ったまま書き込みに失敗する可能性がある。

### 4. `BackupOutputWriter` 境界で mode と上書きをテストする

`BackupOutputWriterTest` では `ContentResolver` の framework method を直接 mock することに依存せず、`BackupOutputWriter` に URI と mode を受け取る internal な output-stream opener seam を設けて次を検証する。production の `@Inject` constructor はこの seam に `context.contentResolver.openOutputStream(uri, mode)` を渡し、test は任意の戻り値または例外を返す lambda を渡す。公開APIとDI利用側は変更しない。

- `writeToUri` が対象 URI を正確に `"wt"` で一度だけ開く。
- mode-sensitive storage test double は、初期状態として大きい byte sequence を実際に保持する。open 時の mode に `t` が含まれる場合だけ logical size を 0 にし、含まれない場合は先頭から上書きしても元の logical size と後続 byte を保持する。`"wt"` 経路の処理後は内容と長さが小さい新 sequence のみに一致し、対照となる `"w"` 経路では旧末尾が残ることを確認する。
- `openOutputStream(uri, "wt") == null` は `BackupOutputException` になる。
- `openOutputStream(uri, "wt")` が例外を投げた場合は、`"w"` で再試行しない。
- block 成功時と失敗時に `BackupOutputWriter` が finally で close を試行する既存契約を維持する。
- `BackupZipWriterTest` の既存 close-failure test により、ZIP 書き込み成功後に underlying output close が失敗しても `isSuccessful()` が false になることを回帰確認する。block failure と finally close failure の例外優先順位は本変更で変更しない。

provider 固有の実装差を JVM test で再現したと主張せず、アプリが公式 truncate mode を要求することをテスト対象とする。実機 provider の契約違反は本変更の保証外とする。

### 5. 実装後監査で判明した test gap を是正する

初回実装後の監査で、production の `"wt"` 修正自体は正しい一方、次の test gap が判明した。

- `TruncationTestStream` の truncate 経路は空の `ByteArrayOutputStream` から開始しており、保持済み旧内容を消去する状態遷移を検証していない。
- `writeToUri_neverFallsBackToWMode` は `"wt"` 成功時の test であり、`"wt"` open 例外時に元の例外を伝播して `"w"` へ再試行しない契約を検証していない。exact mode test とも重複する。
- test comment の「`ContentResolver.openOutputStream` は final method のため例外を stub できない」という断定は、実際の testability を示す根拠にならない。framework mock の可否に依存せず、明示的な seam で例外経路を検証する。
- `BackupOutputWriter` の class-level KDoc が `openOutputStream(uri)` のままで、実際の `"wt"` mode を説明していない。

是正では production の SAF mode、例外変換、fallback、stream lifecycleを変えない。internal constructor または同等の最小 seam によって opener だけを差し替え可能にし、test のために新しい公開API、Hilt binding、外部dependencyを追加しない。

## Error Cases / Compatibility

- `"wt"` で stream を取得できる: ZIP を直接書き込み、全 entry と close が成功した場合のみ成功を返す。
- stream が `null`: `BackupOutputWriter` が `BackupOutputException` を発生させ、repository が `BackupExportResult.Failure` へ変換する。
- provider が `"wt"` をサポートしない: `BackupOutputWriter` は元の open 例外を変更せず伝播し、repository が `BackupExportResult.Failure` へ変換する。非 truncate mode へ再試行しない。
- ZIP write/finish/close が失敗する: 現在の `BackupZipWriter` と repository の失敗契約を維持する。
- provider が `"wt"` を受理しながら truncate しない: Android provider 契約外であり、汎用 SAF API だけで安全に補正できないため保証外とする。
- API compatibility: `"wt"` は SAF が導入された API 19 以降の既存 mode であり、新しい minSdk 条件や依存関係を追加しない。

## Risks / Trade-offs

- [Risk] 一部 provider が `"w"` は受理しても `"wt"` を拒否し、従来成功していた保存先で export が失敗する。 → 破損の可能性がある成功より安全な失敗を優先し、既存の失敗 Snackbar と詳細ログを使用する。
- [Risk] MockK test は実在する全 provider の挙動を保証しない。 → exact mode と fallback 禁止を自動テストで固定し、Android 公式 mode contract を実装上の境界とする。
- [Risk] `"wt"` で open した後に ZIP 書き込みが失敗すると、既存ドキュメントは空または不完全になる。 → これは上書き型 SAF 出力の既存制約であり、成功扱いせず不完全な可能性をログへ残す現行契約を維持する。atomic replace は別 change とする。

## Migration Plan

1. `BackupOutputWriter.writeToUri` の SAF open mode を `"wt"` へ変更する。
2. `BackupOutputWriter` の mode、上書き、open failure、fallback 禁止、close の test を追加する。
3. repository/export の既存 unit tests と Android CI を実行する。
4. Room/DataStore migration は不要である。
5. 問題が発生した場合は本変更を revert する。ただし revert は以後のアプリ動作だけを戻すもので、すでに truncate または部分書き込みされた出力先ドキュメントは復元できない。`"w"` への runtime fallback は追加しない。

## Implementation Contract

- 変更対象の production code は `BackupZipWriter.kt` 内の `BackupOutputWriter` に限定する。`writeToUri` の動作に加え、test用に output-stream opener を差し替える internal constructor または同等の最小 seam と、class-level KDocだけを変更してよい。
- `ContentResolver.openOutputStream` の mode は文字列 `"wt"` とし、`"w"`、`"rw"`、`"rwt"` を代替として使用しない。
- `"wt"` での open が `null` または例外の場合、同じ URI を別 mode で再度開かない。
- `BackupOutputWriter` 境界では `null` だけを `BackupOutputException` に変換し、open 例外は変更せず伝播する。`BackupRepositoryImpl.writeZip` 境界では両方を既存処理で `BackupExportResult.Failure` に変換する。
- `BackupZipWriter` の ZIP entry 生成、close、`isSuccessful` 判定を変更しない。
- `BackupRepositoryImpl`、`BackupViewModel`、`BackupScreen` の結果/UI契約を変更しない。
- `ParcelFileDescriptor`、`Os.ftruncate`、URI delete/recreate、一時 ZIP、外部ストレージ権限、FileProvider、追加 dependency を導入しない。
- test は `"wt"` の指定だけでなく、旧内容を実際に保持した test double を小さい新内容で上書きした後に旧末尾が残らないことを検証する。対照として同じ test double の非 truncate mode では旧末尾が残ることも確認する。
- test は opener が `"wt"` open で固有の例外instanceを投げたとき、同じinstanceが変更されず伝播し、opener呼び出しが1回だけで、`"w"` fallback がないことを検証する。
- 成功時の `"wt"` / `"w"` 呼び出し回数だけを重複検証する test は統合または削除する。
- 新しい class/interface を追加する場合は KDoc を annotation より前に置く。可能な限り新しい production typeを追加せず、internal constructor と function type の seam を優先する。

## Testing Strategy

- JVM unit test:
  - `BackupOutputWriter.writeToUri` が `openOutputStream(uri, "wt")` を呼ぶ。
  - 大きい既存内容を小さい内容で上書きした test double の最終 byte length と内容が新しい出力だけに一致する。
  - `openOutputStream` が `null` を返すと `BackupOutputException` になる。
  - opener が `"wt"` open で固有の例外instanceを投げた場合、そのinstanceを変更せず伝播し、別modeで再試行しない。
  - block が成功または失敗した後の stream lifecycle が既存契約どおりである。
  - `BackupZipWriterTest` の既存 close-failure test が、underlying output close 失敗を成功扱いしない。
  - mode-sensitive storage test double は旧内容を保持して開始し、`"wt"` では旧末尾が消え、`"w"` 対照経路では旧末尾が残る。
- Regression:
  - `BackupZipWriterTest` と backup repository 関連 tests を実行し、ZIP内容と失敗判定が変わらないことを確認する。
- CI:
  - repository の `Android CI` workflow で unit tests と CI APK build を実行する。

## Open Questions

なし。任意 provider に対する atomic replace や provider 契約違反の検出が必要になった場合は、P2-5へ混在させず別 change で扱う。
