# bookmark Specification

## Purpose
TBD - created by archiving change refactor-bookmark-bottom-sheet. Update Purpose after archive.
## Requirements
### Requirement: ブックマークシート用ステートホルダー
システムはブックマークシートの状態と操作を管理するステートホルダーを提供することを SHALL 要求する。

#### Scenario: スレッド画面からシートを開く
- **WHEN** ユーザーがスレッド画面でブックマークシートを開く
- **THEN** ステートホルダーは該当スレッドのグループ一覧と選択状態を提供する

### Requirement: targetsリストによる統一処理
システムは単体/一括の区別を持たず、targetsリストに対して同一の編集処理を適用することを SHALL 要求する。

#### Scenario: 一括選択にグループを適用する
- **WHEN** ユーザーがブックマーク一覧の一括選択でグループを適用する
- **THEN** 選択された全件にグループが反映されシートが閉じる

### Requirement: 板とスレの混在禁止
システムはtargetsリストに板とスレが混在する構成を許可しないことを SHALL 要求する。

#### Scenario: 板とスレが混在した場合
- **WHEN** targetsリストに板とスレが混在する
- **THEN** ステートホルダーは混在を受け付けない

### Requirement: シート外のブックマーク状態保持
システムはシート非表示時でもブックマーク状態を表示できるように画面ViewModelが状態を保持することを SHALL 要求する。

#### Scenario: シート非表示でも星アイコンが更新される
- **WHEN** 板またはスレッドがブックマークされる
- **THEN** シートが閉じていても星アイコンが更新される

