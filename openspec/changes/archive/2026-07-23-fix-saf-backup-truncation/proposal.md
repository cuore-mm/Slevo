## Why

バックアップ ZIP の SAF 出力は `ContentResolver.openOutputStream(uri, "w")` を使用しているが、Android の契約上 `"w"` は provider によって既存内容を truncate しない場合がある。既存バックアップをより小さい ZIP で上書きしたときに旧ファイルの末尾が残り、破損または不要な後続データを含むバックアップを成功扱いする可能性があるため、truncate を明示した出力契約へ変更する。

## What Changes

- SAF のバックアップ出力を write-only の truncate 要求で開き、既存ドキュメントの内容を新しい ZIP の書き込み開始前に消去する。
- truncate 対応モードで保存先を開けない provider では、非 truncate モードへフォールバックせずバックアップ作成を失敗扱いにする。
- 保存先 open、ZIP 書き込み、stream close の既存失敗契約を維持しつつ、truncate モードの指定と失敗伝播を回帰テストで固定する。
- 任意の SAF provider に対して保証できない delete/recreate、seek、`ftruncate`、一時 ZIP 経由の置換は導入しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `backup-export`: 既存 URI を上書きするバックアップ出力は truncate を明示して開き、truncate 対応モードで開けない場合は成功扱いしない。

## Impact

- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/BackupZipWriter.kt` の `BackupOutputWriter.writeToUri`
- 対象テスト: `BackupOutputWriter` の SAF open mode、open failure、stream lifecycle、および小さい内容による上書きの回帰テスト
- Android API: `ContentResolver.openOutputStream(Uri, "wt")`
- ZIP 形式、Room schema、DataStore schema、バックアップ UI、外部ストレージ権限、依存ライブラリには変更なし
