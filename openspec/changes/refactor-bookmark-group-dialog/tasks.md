## 1. Implementation
- [ ] 1.1 GroupDialogState を追加し、必要な最小状態を定義する。
- [ ] 1.2 GroupDialogController を実装し、追加/更新/削除とダイアログ制御を共通化する。
- [ ] 1.3 SingleBookmarkState と BookmarkUiState に GroupDialogState を内包させる。
- [ ] 1.4 BoardBookmarkViewModel / ThreadBookmarkViewModel を GroupDialogController に切り替える。
- [ ] 1.5 BookmarkViewModel のグループ編集/ダイアログ処理を GroupDialogController に委譲し、一覧操作に集中させる。
- [ ] 1.6 UI 参照箇所を新しい状態に合わせて更新する。
- [ ] 1.7 BookmarkGroupEditor を削除または退役し、不要なコードを整理する。
- [ ] 1.8 影響範囲のテストを更新する（必要な場合）。

## 2. Validation
- [ ] 2.1 `gh-ci-run "Android CI"` を実行する。
