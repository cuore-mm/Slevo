# post-dialog Specification

## Purpose
TBD - created by archiving change add-post-dialog-controller. Update Purpose after archive.
## Requirements
### Requirement: PostDialogコントローラの提供
システムは返信とスレ立てのPostDialogを共通で扱うためのコントローラを提供することを SHALL 要求する。

#### Scenario: 返信とスレ立てで共通の入力状態を扱う
- **WHEN** Thread画面またはBoard画面でPostDialogを開く
- **THEN** コントローラは共通の入力状態（名前/メール/本文/タイトル）を提供する

### Requirement: 投稿実行の差し替え
システムは投稿実行を差し替え可能な実行インターフェースで提供し、結果に応じたUI状態を更新することを SHALL 要求する。

#### Scenario: 返信とスレ立てで異なる投稿実装を使用する
- **WHEN** 返信の投稿処理が実行される
- **THEN** 返信用の実行実装が呼び出され、結果に応じて確認画面またはエラー画面が表示される

### Requirement: 投稿履歴候補の管理
システムは投稿フォームの名前/メール履歴候補を更新・削除できることを SHALL 要求する。

#### Scenario: 入力変更で履歴候補が更新される
- **WHEN** ユーザーが名前またはメールを変更する
- **THEN** コントローラは履歴候補の更新を委譲し、UI状態に反映する

### Requirement: 投稿成功時の履歴記録
システムは投稿が成功した場合に名前/メールの履歴を記録することを SHALL 要求する。

#### Scenario: 投稿成功で履歴を記録する
- **WHEN** 返信またはスレ立ての投稿が成功する
- **THEN** コントローラは投稿履歴を記録し、画面固有の後処理へ結果を伝える

### Requirement: 投稿ダイアログ内サムネイルの shared transition 識別
システムは投稿ダイアログ本文で表示する画像サムネイルに対して、ダイアログ表示文脈を含む shared transition 識別子を適用しなければならないMUST。

#### Scenario: 背後画面と同一 URL があってもサムネイル表示が欠落しない
- **WHEN** 投稿ダイアログ表示中に、背後の画面と同一画像 URL を含むサムネイルが同時に存在する
- **THEN** システムは投稿ダイアログ側サムネイルを欠落なく描画する

#### Scenario: 生成ロジックを共通化して命名揺れを防ぐ
- **WHEN** 投稿ダイアログが shared transition 識別子を生成する
- **THEN** システムは他経路と同じ共通ロジックで識別子を生成する

### Requirement: 投稿ダイアログから画像ビューアへの文脈伝播
システムは投稿ダイアログ内サムネイルのタップで画像ビューアを開く際、遷移元で使用した表示文脈を画像ビューアへ受け渡さなければならないMUST。

#### Scenario: 投稿ダイアログからの遷移でキー契約が一致する
- **WHEN** ユーザーが投稿ダイアログ内の画像サムネイルをタップして画像ビューアへ遷移する
- **THEN** システムは遷移元と遷移先で同一文脈の shared transition キーを使用する

