## Why

バックアップ復元時の文字倍率・行間 validation は正の有限値だけを要求しており、表示設定 UI が生成できない極端な値も受理する。現在の UI 許容範囲を共有ドメイン制約として一元化し、将来の範囲変更時に UI と復元 validation が乖離しないようにする。

## What Changes

- 文字倍率 3 項目と行間について、許容範囲と既定値を所有する共有の canonical 制約を導入する。
- 表示設定 UI の slider 範囲・reset 値、DataStore の欠損時 fallback、`ThreadUiState` の既定値を canonical 制約から参照する。
- バックアップ reader は canonical 制約を使い、NaN、正負の infinity、下限未満、上限超過を既存の invalid-backup 経路で拒否する。
- 現在有効な値、JSON field 名、`Float` 型、backup format version、DataStore key、および画面表示・文言・layout・accessibility を変更しない。
- 境界・有限性・参照 drift を production の canonical 制約から導出したテストで固定し、reader の実装やテストに数値範囲を重複させない。

## Capabilities

### New Capabilities

- `backup-text-setting-validation`: 共有の表示設定制約と、それに追従するバックアップ文字設定 validation の契約を定義する。

### Modified Capabilities

なし。

## Impact

- UI/domain: `DisplaySettingsBottomSheet.kt`、`ThreadUiState.kt`、共有制約を配置する `data/model`。
- DataStore: `SettingsLocalDataSourceImpl.kt` の既定値参照。key、保存型、migration は変更しない。
- Backup: `BackupReader.kt` の settings validation。serialized schema と既存 invalid-backup UI は変更しない。
- Tests: `BackupReaderTest.kt`、共有制約の unit test、表示設定 UI の canonical 範囲参照を検証する Compose test。
- restore-picker の multi-launch finding と post-implementation audit は対象外とする。
