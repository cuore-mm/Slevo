## ADDED Requirements
### Requirement: PostDialogStateによる投稿状態の一元化
システムは投稿ダイアログの状態をPostDialogStateで一元管理し、BoardUiStateとThreadUiStateはpostDialogStateとして保持するSHALL。

#### Scenario: Board画面で投稿ダイアログを開く
- **WHEN** Board画面が投稿ダイアログを表示する
- **THEN** BoardUiState.postDialogStateが画面描画の唯一の状態ソースになる

#### Scenario: Thread画面で返信ダイアログを開く
- **WHEN** Thread画面が返信ダイアログを表示する
- **THEN** ThreadUiState.postDialogStateが画面描画の唯一の状態ソースになる

### Requirement: PostDialogStateのフォーム入力とプレースホルダ
システムはPostDialogStateにフォーム入力値とnamePlaceholderを保持するSHALL。

#### Scenario: 名前のプレースホルダを表示する
- **WHEN** 投稿ダイアログが名前入力欄を描画する
- **THEN** PostDialogState.namePlaceholderがプレースホルダとして表示される

### Requirement: PostDialogStateAdapterの更新責務
システムはPostDialogStateAdapterを通じてUiState内のpostDialogStateを更新するSHALL。

#### Scenario: Controllerが投稿状態を更新する
- **WHEN** PostDialogControllerが投稿状態を更新する
- **THEN** PostDialogStateAdapterがUiState.postDialogStateに反映する
