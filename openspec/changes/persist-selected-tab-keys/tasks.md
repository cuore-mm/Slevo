## 1. Tabs selected key永続化API

- [x] 1.1 `SlevoPreferenceDataStores.kt`に`selected_board_tab_key`と`selected_thread_tab_key`の`stringPreferencesKey`を追加し、既存`tabs.preferences_pb`を使用してRoom schemaやbackup JSONを変更していないことを差分で確認する。
- [x] 1.2 `TabsLocalDataSource.kt`と`TabsLocalDataSourceImpl.kt`へ板・スレッドkeyの`Flow<String?>`とnullable setterを追加し、null指定時は対象Preferences entryをremoveする。interfaceと実装のKDocをリポジトリ規約に合わせる。
- [x] 1.3 `TabsRepository.kt`へselected keyのobserve/set委譲を追加し、`TabsRepositoryThreadStateTest.kt`などの`FakeTabsLocalDataSource`と全実装クラスを新APIへ追従させる。
- [x] 1.4 LocalDataSource/Repositoryテストで初期null、保存、上書き、null削除、板・スレッドkeyの相互独立性を検証する。

## 2. 共通selection repairの末尾化

- [x] 2.1 `TabProjectionPrimitives.kt`の`resolveTabPresentation`で、loaded非空かつpending causeなしのnull・確定無効keyを`tabs.last()`のstable keyへ補正する。`Loading`、`PendingMissing`、`Empty`、有効keyの規則は変更しない。
- [x] 2.2 `TabProjectionPrimitivesTest.kt`および先頭補正を期待する既存Board/Threadテストを更新し、null・不正keyは末尾、有効keyは維持、pending missingは書換えなし、0件はnullになることを検証する。

## 3. Board選択の復元と保存

- [x] 3.1 `BoardTabsCoordinator.bind`で`TabsRepository.observeSelectedBoardTabKey().first()`と初回canonical一覧の両方が揃うまでLoadingを維持し、最初の`reconcileCanonical`へ復元keyを渡して`canonicalTabs`、`selectedKey`、`presentation`を同じ`_state.update`で確定する。
- [x] 3.2 `BoardTabsCoordinator`に確定selected keyを直列保存する単一writerを追加し、初回末尾補正、既存タブの明示選択、route登録後の選択、pending causeなしのcanonical補正を`TabsRepository.setSelectedBoardTabKey`へ反映する。
- [x] 3.3 BoardのDelete/BulkDeleteはrepository成功とcanonical確認後だけ最終selected keyを保存し、失敗・cancelでは削除前の保存値を維持する。loaded空一覧または最後のタブ削除確定時はnullを保存する。
- [x] 3.4 `BoardTabsCoordinatorTest.kt`で有効保存keyの初回復元、保存なし・不正保存keyの末尾補正、最初のloaded emissionのatomicity、連続選択の最終値、削除成功・失敗・最後の削除を検証する。

## 4. Thread選択の復元と保存

- [x] 4.1 `ThreadTabsCoordinator.bind`で`TabsRepository.observeSelectedThreadTabKey().first()`を初回canonical投影前に取得し、`publishProjectedTabs(requestedSelection = restoredKey)`から最初のloaded `ThreadTabsLoadState`、selected key、`TabPresentationState`を構成する。
- [x] 4.2 `ThreadTabsCoordinator.publishThreadPresentation`のnull・確定無効補正を先頭から末尾keyへ変更し、確定selected keyを`TabsRepository.setSelectedThreadTabKey`へ直列保存する単一writerを追加する。`ThreadId.value`を保存形式として使用する。
- [x] 4.3 ThreadのDelete/BulkDeleteは`removePending`でcanonical確認後のselectionが確定してから保存し、pending中、失敗、cancelでは保存値を先行変更しない。loaded空一覧または最後のタブ削除確定時はnullを保存する。
- [x] 4.4 `ThreadTabsCoordinatorTest.kt`で有効保存keyの初回復元、保存なし・不正保存keyの末尾補正、初回loaded表示、連続選択、pending Delete中の保存値維持、削除成功・失敗・最後の削除を検証する。

## 5. Store・UI・互換性の回帰検証

- [x] 5.1 `TabSessionStoreTest.kt`で板・スレッドのselected keyが独立して復元され、`ensureAndSelectBoardTab`、`ensureAndSelectThreadTab`、登録確認APIの成功後に対応する最終keyが永続化されることを検証する。
- [x] 5.2 `BbsRouteScaffoldSelectionTest.kt`と全画面Tabs関連テストで、保存keyに一致するPager/tab cardを表示し、保存keyなしまたは削除済みkeyでは補正後の末尾タブを表示することを検証する。`currentPage`を復元fallbackとして追加しない。
- [x] 5.3 DataStore読込遅延と書込失敗をfakeで再現し、読込完了前はLoadingを維持すること、書込失敗でruntime selectionを失わないこと、後続selectionで最新keyの保存を再試行できることを検証する。
- [x] 5.4 `BackupTabsJson.kt`、`BackupDataMapper.kt`、`PendingRestoreDataStoreWriter.kt`を変更していないことと、既存backup fixture・Room migrationテストがそのまま通ることを確認する。
- [x] 5.5 追加・変更したclass/interfaceと非自明関数のKDoc、30行超関数のセクションコメント、復元guard、末尾fallback、stable key変換コメントをリポジトリ規約に合わせる。Compose Preview関数にはdoc commentを追加しない。
- [ ] 5.6 CIで`./gradlew build`と`./gradlew test`を実行し、build、unit test、既存backup・migration回帰が全て成功するまで修正する。実機またはエミュレータで初回起動は末尾、板・スレッド選択後の再起動は各選択タブ復元、最後のタブ削除後は空状態になることを確認する。
