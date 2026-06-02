## 1. 保存経路の調査

- [ ] 1.1 `ThreadTabCoordinator.updateThreadScrollPosition` の現在の呼び出し元と保存経路を確認する
- [ ] 1.2 `TabsRepository.saveOpenThreadTabs` が担当しているタブ一覧保存の責務を確認する
- [ ] 1.3 `OpenThreadTabDao` と `OpenThreadTabEntity` の scroll column 名と既存テスト構成を確認する

## 2. DAO / Repository API の追加

- [ ] 2.1 `OpenThreadTabDao` に指定 `threadId` の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` だけを更新するクエリを追加する
- [ ] 2.2 DAO 更新クエリは更新件数を返し、対象タブが存在しない場合に no-op として扱えるようにする
- [ ] 2.3 `TabsRepository` にスレッドタブスクロール位置専用の更新メソッドを追加する
- [ ] 2.4 専用メソッドの KDoc で、タブ一覧構造保存ではなく scroll columns のみを更新する API であることを明示する

## 3. Coordinator 保存経路の切り替え

- [ ] 3.1 `ThreadTabCoordinator.updateThreadScrollPosition` から `observeOpenThreadTabs().first()` と list `map` を削除する
- [ ] 3.2 `ThreadTabCoordinator.updateThreadScrollPosition` で専用 Repository メソッドを呼び出す
- [ ] 3.3 タブの追加、削除、並び替え、pin 切替などの既存 `saveOpenThreadTabs()` 経路が維持されていることを確認する

## 4. テスト追加・更新

- [ ] 4.1 DAO または Repository テストで、対象 `threadId` の scroll columns だけが更新されることを確認する
- [ ] 4.2 他タブの scroll columns、対象タブの `sortOrder`、`isPinned` が変わらないことを確認する
- [ ] 4.3 存在しない `threadId` へのスクロール位置保存が no-op になり、タブやスレッド状態を新規作成しないことを確認する
- [ ] 4.4 Coordinator テストで、スクロール位置保存が一覧取得・一覧全体保存ではなく専用 Repository メソッドを呼ぶことを確認する

## 5. 回帰確認

- [ ] 5.1 スレッド画面の手動スクロール後に保存済み位置へ復元されることを確認する
- [ ] 5.2 自動スクロール中の定期保存、タブ切り替え保存、画面離脱保存の既存挙動が維持されることを確認する
- [ ] 5.3 タブ追加、削除、並び替え、pin 切替の既存挙動が維持されることを確認する
- [ ] 5.4 Android CI でビルドとテストが成功することを確認する
