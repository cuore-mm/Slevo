## 1. 状態と責務の棚卸し

- [x] 1.1 `ThreadUiState`、`BoardUiState`、`ThreadTabInfo`、`BoardTabInfo`、`TabSessionStore` の保持項目を一覧化し、正本を「軽量 `TabInfo`」「UI `SessionState`」「Repository / DB / UseCase」「合成 `UiState`」に分類する
- [x] 1.2 `ThreadViewModel` と `BoardViewModel` のイベント、監視 Flow、Repository 依存、初期化処理、解放処理を洗い出す
- [x] 1.3 per-tab ViewModel でなければ保持できない状態が残っていないか確認し、残る場合は Session State または UseCase へ移す方針を決める

## 2. タブセッション状態の受け皿整備

- [x] 2.1 `ThreadTabInfo` / `BoardTabInfo` に残す項目を、タブ識別子、タイトル、pin、order、復元に必要なスクロール位置などの軽量メタ情報に限定する
- [x] 2.2 `ThreadSessionState` 相当のモデルを設計し、検索条件、表示モード、ポップアップスタック、投稿ダイアログ下書き、自動スクロール状態などを保持できるようにする
- [x] 2.3 `BoardSessionState` 相当のモデルを設計し、検索・ソート条件、新スレ投稿ダイアログ下書きなどを保持できるようにする
- [x] 2.4 `TabSessionStore` / coordinator に、タブ key ごとの Session State 取得・更新 API を追加する
- [x] 2.5 タブ削除時に対象 Session State だけが削除され、共通客観状態や履歴状態を削除しないことをテストする
- [x] 2.6 検索条件、表示モード、ポップアップスタック、投稿ダイアログ下書き、自動スクロール状態を永続タブ状態へ保存しないことを確認する

## 3. データ取得・表示変換ロジックの分離

- [x] 3.1 スレッド本文取得、レス表示行生成、NG 適用、検索適用、ツリー派生情報、新着計算を UseCase / coordinator に切り出す
- [x] 3.2 板スレ一覧取得、ソート、フィルタ、NG 適用、ブックマーク合成を UseCase / coordinator に切り出す
- [x] 3.3 切り出した UseCase を `ThreadRouteViewModel` / `BoardRouteViewModel` から呼ぶ形へ変更し、既存挙動が維持されることを単体テストで確認する

## 4. Route 単位 ViewModel への移行

- [ ] 4.1 `BoardRouteViewModel` を導入し、選択中板タブ key、`BoardSessionState`、Repository / UseCase、Settings、NG、Bookmark を合成して `BoardUiState` を公開するようにする
- [ ] 4.2 `ThreadRouteViewModel` を導入し、選択中スレッドタブ key、`ThreadSessionState`、Repository / UseCase、Settings、NG、Bookmark、既読状態を合成して `ThreadUiState` を公開するようにする
- [ ] 4.3 `ThreadRouteViewModel` / `BoardRouteViewModel` に tab key 指定の `observeUiState` / `uiStateFor` 相当 API を追加し、要求された tab key の `UiState` Flow を遅延生成・再利用できるようにする
- [ ] 4.4 `UiState` Flow の共有方式を購読中のみ動作する形にし、composition から外れたタブの重い合成が継続しないことを確認する
- [ ] 4.5 選択中タブ key が変わっても ViewModel を再生成せず、同じ route ViewModel で表示状態を再合成することを確認する
- [ ] 4.6 refresh / reload が ViewModel 再生成ではなく対象タブ key のデータ更新として動作するようにする
- [ ] 4.7 自動スクロールに伴う定期 reload / refresh を表示中スレッドタブのみに限定し、非表示タブを自動更新しないようにする
- [ ] 4.8 開いている全タブの更新は自動更新ではなく、ユーザーの明示的な一括更新操作として扱う

## 5. Scaffold / Pager の接続変更

- [ ] 5.1 `BbsRouteScaffold` が Pager ページごとに `getOrCreateBoardViewModel` / `getOrCreateThreadViewModel` を呼ばない構造へ変更する
- [ ] 5.2 Pager ページは自身が compose されたタイミングで tab key 指定の `UiState` Flow を購読し、アプリ側で previous/current/next を明示管理しない構造にする
- [ ] 5.3 全 open tabs 分の完全な `UiState` を ViewModel 側で常時 combine していないことを確認する
- [ ] 5.4 スクロール位置保存・復元が Session State を通じて従来通り動くことを確認する
- [ ] 5.5 タブ一覧シート、横スワイプ、別種別タブ選択で navigation back stack と selected key の既存挙動が維持されることを確認する

## 6. per-tab ViewModel registry の縮小・削除

- [ ] 6.1 `TabViewModelRegistry` の利用箇所を route-level ViewModel または Session State API に置き換える
- [ ] 6.2 `BaseViewModel.release()` と `onCleared()` 手動呼び出しに依存する処理を削除する
- [ ] 6.3 Assisted factory が per-tab ViewModel 生成専用になっている場合は削除し、必要な場合のみ route ViewModel 用に整理する
- [ ] 6.4 registry 削除後もタブ削除、画面破棄、構成変更で監視ジョブが適切に終了・再開されることを確認する

## 7. 回帰確認とドキュメント整理

- [ ] 7.1 複数板タブ・複数スレッドタブを開いた状態で、タブ切替、戻る操作、タブ削除、再追加の回帰テストを追加または更新する
- [ ] 7.2 スレッドの新着表示、既読更新、スクロール復元、検索、ポップアップ、投稿ダイアログ、自動更新の回帰テストを追加または更新する
- [ ] 7.3 板一覧の更新、ソート、フィルタ、NG、ブックマーク表示、新スレ投稿ダイアログの回帰テストを追加または更新する
- [ ] 7.4 アプリ再起動後に検索条件、ポップアップ状態、投稿下書き、自動スクロール状態が永続復元されないことを確認する
- [ ] 7.5 自動スクロール中の定期更新が表示中スレッドタブだけに作用し、非表示タブへ作用しないことを確認する
- [ ] 7.6 `./gradlew test` と必要な Android / Compose テストまたは CI を実行し、ViewModel 所有単位変更による退行がないことを確認する
- [ ] 7.7 実装後に OpenSpec の該当仕様と設計判断が実装内容と一致しているか確認する
