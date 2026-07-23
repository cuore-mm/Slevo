## Why

バックアップ復元は選択された ZIP を信頼せずに処理する必要があるが、現在の `BackupReader` は JSON entry を無制限に `readBytes()` し、DB entry も上限なしで一時ファイルへ展開している。高圧縮または破損した ZIP によりヒープ枯渇、アプリ領域のストレージ枯渇、process crashを起こせるため、実際の展開byte数に基づくresource limitを導入する。

## What Changes

- バックアップ形式にentry別・合計の展開後サイズ上限とentry数上限を定義する。
- `BackupReader` は ZIP headerの申告値だけを信用せず、streaming中に実際に読み取ったbyte数を計測して上限超過時に中止する。
- DB entryはbounded streamingを維持し、JSON entryはbounded memory readへ変更する。
- 上限超過を無効なバックアップとして扱い、上限超過・I/O失敗・malformed ZIP・validation失敗・cancellationを含むすべての不成功経路で作成途中の一時DBを削除する。
- アプリ自身が作成したバックアップを同じversionで復元できるよう、export側でも復元resource policyを超えるバックアップを成功扱いしない。
- 境界値、高圧縮データ、合計上限、cleanup、export/restore整合性を自動テストする。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `backup-restore`: ZIP entryの展開後サイズ・合計サイズ・entry数を制限し、超過したバックアップを安全に拒否する。
- `backup-export`: 同じversionが復元できないresource limit超過バックアップを作成成功として扱わない。

## Impact

- 対象コード: `BackupReader.kt`、`BackupRepositoryImpl.kt`またはexport前検証の適切な既存境界、必要な共有limit policy/helper
- 対象テスト: `BackupReaderTest.kt`、backup export/repository関連tests
- エラー結果: size limit超過は`BackupRestoreResult.Invalid`、export limit超過は`BackupExportResult.Failure`
- ZIP entry名、manifest schema、Room/DataStore schema、復元state machine、UI文言、外部dependencyには変更なし
