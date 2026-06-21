## MODIFIED Requirements

### Requirement: Board/Thread Route ViewModel の初期化シーケンス統一
BoardRouteViewModel と ThreadRouteViewModel は、画面 route 単位の ViewModel として初期化され、選択中タブ key の変更に応じて表示状態を再合成しなければならない（MUST）。既存のタブ単位 BoardViewModel / ThreadViewModel を初期化対象として扱ってはならない（MUST NOT）。初期化フェーズは以下を含み、タブごとの ViewModel インスタンス生成を前提としてはならない（MUST NOT）。
- route 引数または選択中タブ key から初期 placeholder を反映する
- タブセッション状態、永続データ、設定、ブックマーク、NG の監視を開始する
- 選択中タブに対応する BoardInfo / ThreadInfo と表示データを合成する
- 更新や再読み込み要求を対象タブ key に紐づけて実行する

#### Scenario: 新しいキーで初期化する
- **WHEN** Board/Thread の route ViewModel が初期化される
- **THEN** route 引数または選択中タブ key の placeholder 反映 → 共通監視開始 → 選択中タブの表示状態合成の順に実行する

### Requirement: 初期化ガードと強制再初期化の統一
BoardRouteViewModel と ThreadRouteViewModel は、route 単位の初期化を重複実行せず、選択中タブ key が変わった場合は ViewModel を作り直さずに表示状態を再合成しなければならない（MUST）。明示的な再読み込み要求がある場合は、対象タブ key に対応するデータ更新を実行しなければならない（MUST）。

#### Scenario: 同一キーで初期化が呼ばれる
- **WHEN** route 単位 ViewModel の初期化が同じ route に対して再度呼ばれる
- **THEN** 共通監視ジョブを重複起動せず、既存の監視と選択中タブの表示状態合成を継続する

#### Scenario: 強制再初期化が要求される
- **WHEN** refresh/reload などの強制再読み込みが呼ばれる
- **THEN** ViewModel を再生成せず、対象タブ key に対応するデータ更新を実行して最新の表示に更新する

#### Scenario: 選択中タブが変更される
- **WHEN** ユーザーが板またはスレッドの別タブへ切り替える
- **THEN** システムは既存の route 単位 ViewModel で新しい選択中タブ key のタブセッション状態とデータを合成する
