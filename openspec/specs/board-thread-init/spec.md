# board-thread-init Specification

## Purpose
TBD - created by archiving change update-board-thread-init-flow. Update Purpose after archive.
## Requirements
### Requirement: Board/Thread ViewModel の初期化シーケンス統一
BoardViewModel と ThreadViewModel は、初期化時に同じフェーズ順で処理を実行しなければならない（MUST）。
フェーズは以下を含む。
- UIState の基礎情報反映（BoardInfo / ThreadInfo、postDialog の placeholder 等）
- 永続データの補完（boardId の ensure、noname 取得）
- 監視開始（ブックマーク、NG、設定）
- BaseViewModel.initialize の実行

#### Scenario: 新しいキーで初期化する
- **WHEN** Board/Thread の初期化が新しいキーで呼ばれる
- **THEN** UIState 反映 → データ補完 → 監視開始 → BaseViewModel.initialize の順に実行する

### Requirement: 初期化ガードと強制再初期化の統一
BoardViewModel と ThreadViewModel は、初期化キーが同一の場合は重複初期化を抑止し、明示的な再初期化要求がある場合は同じ導線で再初期化しなければならない（MUST）。

#### Scenario: 同一キーで初期化が呼ばれる
- **WHEN** 直前と同じ初期化キーで初期化が呼ばれる
- **THEN** 監視ジョブを重複起動せず、初期化処理をスキップする

#### Scenario: 強制再初期化が要求される
- **WHEN** refresh/reload などの強制再初期化が呼ばれる
- **THEN** 初期化処理を再実行して最新の表示に更新する

