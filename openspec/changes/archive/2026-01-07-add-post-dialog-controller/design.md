## Context
PostDialogはThread画面の返信とBoard画面の新規スレ立てで共通UIを使うが、
状態管理と投稿処理はThreadViewModelPostとThreadCreationControllerに分散している。
重複したフローを統一し、差分は実行層に閉じ込めたい。

## Goals / Non-Goals
- Goals:
  - PostDialogの状態遷移（表示/入力/確認/エラー）を共通化する
  - Repositoryの差分は差し替え可能にして共通化する
  - 既存のPostDialog UIは変更しない
- Non-Goals:
  - 投稿APIの仕様変更
  - 画像アップロード処理の統合
  - 画面ナビゲーションの変更

## Decisions
- PostDialogControllerをViewModel内で保持するコントローラとして実装する
- コントローラはUI状態を直接持たず、StateAdapterでBoard/Threadの状態へ書き戻す
- 投稿処理はPostDialogExecutorで差し替え、成功/確認/失敗の結果をコントローラが反映する
- 投稿成功時の履歴記録はコントローラに集約する
- 投稿成功後の画面固有処理（スレ再読み込み/リスト更新）はコールバックで呼び出す

## Risks / Trade-offs
- Adapterの実装が増え、マッピングミスが起きる可能性がある
  - 対策: 片側ずつ移行し、要所でユニットテストを用意する
- Board側のThreadCreationController置換で影響範囲が広い
  - 対策: 既存のUI状態（CreateThreadFormState）を維持し、変換層で吸収する

## Migration Plan
1. PostDialogControllerとインターフェースを追加
2. ThreadViewModelから移行し、ThreadScaffoldの呼び出しを更新
3. BoardViewModelへ移行し、ThreadCreationControllerを段階的に置き換える
4. 旧コードを削除し、テスト/ビルドで動作確認
