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

## 4. SessionState の実利用化

- [x] 4.1 `ThreadUiState` / `BoardUiState` と `ThreadSessionState` / `BoardSessionState` の重複フィールドを再確認し、揮発 UI 状態の更新元を SessionState に統一する対象一覧を確定する
- [x] 4.2 検索状態、ソート状態、シート / ダイアログ表示、ポップアップスタック、画像メニュー、トースト、ローディング表示、タブスワイプ可否の更新を `TabSessionStore.updateThreadSessionState` / `updateBoardSessionState` 経由に寄せる
- [x] 4.3 既存 `ThreadViewModel` / `BoardViewModel` の移行期間中も、`UiState` を SessionState と Repository / UseCase 由来値から合成される読み取りモデルとして扱い、同じ揮発 UI 状態を `_uiState` と SessionState の両方で更新しないようにする
- [x] 4.4 `pendingPost`、`popupIdGenerator`、`bookmarkSheetHolder`、`postDialogController`、画像メニュー対象、投稿ダイアログ下書きなど per-tab ViewModel インスタンス所有の継続状態を、tab key ごとの SessionState または `TabSessionStore` 配下の session holder へ移す方針で整理する
- [x] 4.5 タブ切替後も検索、ポップアップ、投稿ダイアログ下書き、画像メニュー、自動スクロール状態が対象タブの SessionState から復元され、別タブへ混線しないことを単体テストで確認する
- [x] 4.6 SessionState へ移した揮発 UI 状態が永続タブ状態へ保存されず、タブ削除時に対象タブ分だけ破棄されることを確認する

## 5. Route 単位 ViewModel への移行

- [x] 5.1 `BoardRouteViewModel` を導入し、選択中板タブ key、`BoardSessionState`、Repository / UseCase、Settings、NG、Bookmark を合成して `BoardUiState` を公開するようにする
- [x] 5.2 `ThreadRouteViewModel` を導入し、選択中スレッドタブ key、`ThreadSessionState`、Repository / UseCase、Settings、NG、Bookmark、既読状態を合成して `ThreadUiState` を公開するようにする
- [x] 5.3 `ThreadRouteViewModel` / `BoardRouteViewModel` に tab key 指定の `observeUiState` / `uiStateFor` 相当 API を追加し、要求された tab key の `UiState` Flow を遅延生成・再利用できるようにする
- [x] 5.4 `UiState` Flow の共有方式を購読中のみ動作する形にし、composition から外れたタブの重い合成が継続しないことを確認する
- [x] 5.5 選択中タブ key が変わっても ViewModel を再生成せず、同じ route ViewModel で表示状態を再合成することを確認する
- [x] 5.6 refresh / reload が ViewModel 再生成ではなく対象タブ key のデータ更新として動作するようにする
- [x] 5.7 自動スクロールに伴う定期 reload / refresh を表示中スレッドタブのみに限定し、非表示タブを自動更新しないようにする
- [x] 5.8 開いている全タブの更新は自動更新ではなく、ユーザーの明示的な一括更新操作として扱う

## 6. Scaffold / Pager の接続変更

- [x] 6.1 `BbsRouteScaffold` が Pager ページごとに `getOrCreateBoardViewModel` / `getOrCreateThreadViewModel` を呼ばない構造へ変更する
- [x] 6.2 Pager ページは自身が compose されたタイミングで tab key 指定の `UiState` Flow を購読し、アプリ側で previous/current/next を明示管理しない構造にする
- [x] 6.3 全 open tabs 分の完全な `UiState` を ViewModel 側で常時 combine していないことを確認する
- [x] 6.4 スクロール位置保存・復元が Session State を通じて従来通り動くことを確認する
- [x] 6.5 タブ一覧シート、横スワイプ、別種別タブ選択で navigation back stack と selected key の既存挙動が維持されることを確認する

## 7. per-tab ViewModel registry の縮小・削除

- [x] 7.1 `TabViewModelRegistry` の利用箇所を route-level ViewModel または Session State API に置き換える
- [x] 7.2 `BaseViewModel.release()` と `onCleared()` 手動呼び出しに依存する処理を削除する
- [x] 7.3 Assisted factory が per-tab ViewModel 生成専用になっている場合は削除し、必要な場合のみ route ViewModel 用に整理する
- [x] 7.4 registry 削除後もタブ削除、画面破棄、構成変更で監視ジョブが適切に終了・再開されることを確認する

## 8. 旧 ViewModel 互換レイヤーの棚卸しと Route API 設計

- [x] 8.1 `ThreadScaffold` / `BoardScaffold` の `legacyViewModel(tabKey)` 呼び出しを一覧化し、検索、シート、ソート、ポップアップ、画像メニュー、自動スクロール、スクロール保存、投稿ダイアログ、ブックマーク操作、データ更新、タイトル / 新着同期へ分類する
- [x] 8.2 `ThreadViewModel` / `BoardViewModel` の公開メソッド、内部 job、holder、event source、UseCase / Repository 依存を「SessionState 更新」「session holder 操作」「UseCase 実行」「Repository 同期」「UiState 合成専用」に分類する
- [x] 8.3 `ThreadRouteViewModel` / `BoardRouteViewModel` に追加する tab key 指定 API を定義し、Composable から旧 ViewModel 型を参照しない呼び出し形へ整理する
- [x] 8.4 `ThreadViewModel` / `BoardViewModel` 内の再利用可能な純粋ロジック、mapper、popup helper、PostDialog adapter を移管先（UseCase / transformer / session holder / route private helper）ごとに確定する

## 9. Session holder と UI イベント source の移管

- [x] 9.1 `bookmarkSheetHolder` を `TabSessionStore` 配下の tab key 別 holder へ移し、`BbsRouteScaffold` の `getBookmarkSheetHolder(tab)` が旧 ViewModel を経由しないようにする
- [x] 9.2 `postDialogController` と `PostDialogStateAdapter` を tab key 別 session holder へ移し、投稿ダイアログの表示、入力、履歴、確認画面、投稿実行が対象タブの SessionState にだけ反映されるようにする
- [x] 9.3 画像保存の `ImageSaveCoordinator` / `ImageSaveUiEvent` を tab key 別 event source へ移し、Composable が旧 ViewModel の `imageSaveEvents` を購読しないようにする
- [x] 9.4 タブ削除時に対象 tab key の bookmark holder、post dialog controller、image event source だけが破棄され、別タブの holder / draft / event が残ることを単体テストで確認する
- [x] 9.5 holder 移管後も投稿下書き、ブックマークシート表示、画像保存 permission / toast がタブ切替で混線しないことを確認する

### Implementation notes

- `ui/tabs/session/holder/` に `ThreadTabSessionHolder` / `BoardTabSessionHolder` を新設し、`TabSessionStore` が tab key ごとに生成・キャッシュする。
- 各 holder は `BookmarkBottomSheetStateHolder`、`PostDialogController`、スレッド holder では `ImageSaveCoordinator` + `ImageSaveUiEvent` Flow を保持する。
- `ThreadRouteViewModel` / `BoardRouteViewModel` は `TabSessionStore` の holder API を介して bookmark / post dialog / image save へアクセスし、Scaffold から旧 ViewModel 型を参照しない形に変更した。
- 投稿成功イベントは holder が発行し、RouteViewModel が収集してスレッド再読み込み / 板一覧更新を行う。
- `TabSessionStoreTest` にタブ削除時の holder 破棄と `close()` 時の全 holder 破棄テストを追加した。
- CI（Run #27686888156）でビルド・テストが通過した。

## 10. RouteViewModel による `UiState` 直接合成化

- [ ] 10.1 `ThreadContentLoadUseCase`、`ThreadVisiblePostsUseCase`、Settings / NG / Bookmark / 既読 Flow を `ThreadRouteViewModel` へ注入し、`ThreadUiState` を旧 `ThreadViewModel.uiState` なしで合成する
- [ ] 10.2 板スレ一覧取得、ソート、フィルタ、NG、ブックマーク合成を `BoardRouteViewModel` へ注入し、`BoardUiState` を旧 `BoardViewModel.uiState` なしで合成する
- [ ] 10.3 reload / refresh / auto-scroll / bottom pull refresh / 明示的な全タブ更新を route ViewModel API と UseCase / Repository / coordinator 呼び出しへ移す
- [ ] 10.4 スクロール保存、タイトル更新、新着 / 既読同期、baseline 更新を `TabSessionStore` / coordinator / Repository API 経由に移し、旧 `ThreadTabCoordinator` / 旧 ViewModel の内部 job に依存しないようにする
- [ ] 10.5 `uiStateFor(tabKey)` が `SharingStarted.WhileSubscribed` で購読中だけ重い合成を行い、全 open tabs 分を常時 combine していないことを単体テストで確認する

## 11. 旧 ViewModel / Factory / BaseViewModel の削除

- [ ] 11.1 `ThreadScaffold` / `BoardScaffold` から `legacyViewModel(tabKey)` 呼び出しをすべて削除し、RouteViewModel API / session holder API のみを呼ぶ形にする
- [ ] 11.2 `ThreadRouteViewModel` / `BoardRouteViewModel` から `legacyViewModel(tabKey)`、`viewModelCache`、`ThreadViewModelFactory` / `BoardViewModelFactory`、旧 ViewModel 由来の `disposeResources()` 依存を削除する
- [ ] 11.3 `ThreadViewModel.kt`、`BoardViewModel.kt`、`BaseViewModel.kt`、旧 ViewModel 専用 helper / adapter / factory を削除し、必要なロジックだけを UseCase / transformer / session holder へ移管する
- [ ] 11.4 `ThreadViewModelTest`、`BoardViewModelTest`、旧 ViewModel 前提の RouteViewModel テストを削除または置き換え、RouteViewModel / UseCase / holder / transformer の単体テストへ移す
- [ ] 11.5 旧 ViewModel 削除後もタブ切替、タブ削除、画面破棄、構成変更で `UiState` 購読と session holder が適切に開始・停止することを確認する

## 12. 回帰確認とドキュメント整理

- [ ] 12.1 複数板タブ・複数スレッドタブを開いた状態で、タブ切替、戻る操作、タブ削除、再追加の回帰テストを追加または更新する
- [ ] 12.2 スレッドの新着表示、既読更新、スクロール復元、検索、ポップアップ、投稿ダイアログ、自動更新の回帰テストを追加または更新する
- [ ] 12.3 板一覧の更新、ソート、フィルタ、NG、ブックマーク表示、新スレ投稿ダイアログの回帰テストを追加または更新する
- [ ] 12.4 アプリ再起動後に検索条件、ポップアップ状態、投稿下書き、自動スクロール状態が永続復元されないことを確認する
- [ ] 12.5 自動スクロール中の定期更新が表示中スレッドタブだけに作用し、非表示タブへ作用しないことを確認する
- [ ] 12.6 `./gradlew test` と必要な Android / Compose テストまたは CI を実行し、ViewModel 所有単位変更による退行がないことを確認する
- [ ] 12.7 実装後に OpenSpec の該当仕様と設計判断が実装内容と一致しているか確認する
