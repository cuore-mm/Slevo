## 1. Implementation
- [ ] 1.1 PostDialogStateにnamePlaceholderとフォーム状態を統合し、PostFormStateを共通領域へ移動する。
- [ ] 1.2 BoardUiState/ThreadUiStateにpostDialogStateを追加し、既存の投稿関連フィールドを移行する。
- [ ] 1.3 PostDialogStateAdapterを新しいUiState構造に合わせて更新する。
- [ ] 1.4 PostDialog/BoardScaffold/ThreadScaffoldでPostDialogStateを直接参照するように整理する。
- [ ] 1.5 PostUiStateを削除し、参照箇所を置換する。
- [ ] 1.6 ViewModelの公開プロパティと投稿フローを統一し、不要なラッパーを整理する。
- [ ] 1.7 CIでビルドとユニットテストを実行し、結果を確認する。
