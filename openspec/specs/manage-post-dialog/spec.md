# manage-post-dialog Specification

## Purpose
TBD - created by archiving change remove-post-ui-state. Update Purpose after archive.
## Requirements
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

### Requirement: PostDialogControllerによる投稿履歴管理
システムは投稿履歴の監視・候補更新・削除をPostDialogControllerが管理するSHALL。

#### Scenario: 履歴監視の準備
- **WHEN** PostDialogControllerがboardIdで履歴監視を開始する
- **THEN** 名前/メールの履歴候補を更新し続ける

#### Scenario: 履歴候補の更新
- **WHEN** 入力中の名前/メールが変更される
- **THEN** PostDialogControllerが最新の候補リストを再計算する

#### Scenario: 履歴の削除
- **WHEN** ユーザーが名前/メールの履歴削除を要求する
- **THEN** PostDialogControllerが履歴ストアから該当値を削除する

### Requirement: BaseViewModelからの履歴処理削除
システムは投稿履歴処理をBaseViewModelに保持しないSHALL。

#### Scenario: 履歴処理の移管
- **WHEN** 投稿履歴処理がPostDialogControllerに移管される
- **THEN** BaseViewModelには履歴処理のAPIが存在しない

### Requirement: 画像URL挿入のController集約
システムは画像アップロード後のURL追記をPostDialogControllerが管理するSHALL。

#### Scenario: 画像アップロード成功
- **WHEN** 画像アップロードが成功しURLが取得できる
- **THEN** PostDialogControllerが本文末尾にURLを追記する

### Requirement: 画像アップロード処理の共通化
システムはBoard/Threadの画像アップロード処理を共通のUploaderで実行するSHALL。

#### Scenario: 画面間で同一のアップロードを利用
- **WHEN** BoardまたはThreadで画像アップロードが行われる
- **THEN** 共通Uploaderが実行される

