# backup-text-setting-validation Specification

## Purpose
TBD - created by archiving change validate-backup-text-setting-ranges. Update Purpose after archive.
## Requirements
### Requirement: 文字表示設定の canonical 制約

システムは text scale、header text scale、body text scale に共通の inclusive range と、line height の inclusive range を一つの production domain 定義で所有しなければならない（MUST）。同じ定義は各設定の既定値を所有し、各既定値が対応 range 内の有限値であることを保証しなければならない（MUST）。

#### Scenario: UI control と既定値生成が canonical 制約を使用する

- **WHEN** 表示設定 UI が slider 範囲または reset 値を構成し、DataStore または `ThreadUiState` が欠損時の既定値を生成する
- **THEN** システムは reader とは別の数値 literal ではなく同じ canonical range/default 定義を直接参照する

#### Scenario: 将来 range を変更する

- **WHEN** 開発者が canonical text scale range または line-height range を変更する
- **THEN** 表示設定 UI と backup reader validation は reader 固有の range 編集なしに同じ変更へ追従する

### Requirement: バックアップ文字表示設定の範囲 validation

システムはバックアップ内の `textScale`、`headerTextScale`、`bodyTextScale` を canonical text scale range で、`lineHeight` を canonical line-height range で検証しなければならない（MUST）。判定は下限と上限を含み、NaN、正負の infinity、下限未満、上限超過を拒否しなければならない（MUST）。

#### Scenario: 全 field が canonical range 内である

- **WHEN** 3 個の scale field と line-height field がすべて有限で、それぞれの canonical range 内または endpoint にある v1 backup を reader が処理する
- **THEN** システムは文字表示設定を valid として従来の preview/restore 処理を継続する

#### Scenario: scale field が範囲外である

- **WHEN** `textScale`、`headerTextScale`、`bodyTextScale` のいずれかが canonical text scale range の下限未満または上限超過である
- **THEN** システムは backup を既存の invalid-backup 経路で拒否し、設定を clamp または部分適用しない

#### Scenario: line height が範囲外である

- **WHEN** `lineHeight` が canonical line-height range の下限未満または上限超過である
- **THEN** システムは backup を既存の invalid-backup 経路で拒否し、設定を clamp または部分適用しない

#### Scenario: field が非有限である

- **WHEN** 4 個の対象 field のいずれかが NaN、正の infinity、または負の infinity である
- **THEN** システムは backup を既存の invalid-backup 経路で拒否する

### Requirement: 保存形式と UI behavior の互換性

システムは validation 厳格化後も対象設定を既存の JSON field 名と `Float` 型で保存し、backup format version、DataStore key、範囲内値の意味を変更してはならない（MUST NOT）。表示範囲、reset 結果、文言、layout、interaction、accessibility behavior を変更してはならない（MUST NOT）。

#### Scenario: 現在有効な backup を round-trip する

- **WHEN** canonical range 内の既存文字表示設定を export して restore する
- **THEN** システムは field 名・型・値を維持し、backup format migration なしで同じ設定を復元する

#### Scenario: 表示設定画面を操作する

- **WHEN** ユーザーが表示設定画面を開き、slider または reset を操作する
- **THEN** 操作可能範囲、既定値、表示文言、layout、および accessibility behavior は変更前と同一である

