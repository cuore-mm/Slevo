## Context

`BoardTabsCoordinator`と`ThreadTabsCoordinator`はActivity-retainedなメモリ内にselected stable keyを保持するが、プロセス再生成後はnullから開始する。初回canonical一覧が非空の場合、`resolveTabPresentation`または`publishThreadPresentation`が先頭keyへ補正するため、前回の選択は復元されない。

タブ一覧本体はRoomへ保存される一方、一覧ページの`lastSelectedTabsPage`は`TabsRepository`、`TabsLocalDataSource`、`SlevoPreferenceDataStores.tabs`を通じてPreferences DataStoreへ保存される。selected keyもタブ行ではなく板・スレッドごとに1値の状態であるため、同じTabs用DataStoreが既存分類に合う。現在のRoom schema、バックアップJSON、`TabPresentationState`の型は変更しない。

## Goals / Non-Goals

**Goals:**

- 板とスレッドのselected stable keyを独立して端末内へ保存し、アプリ再起動後に復元する。
- 初回loaded presentationで復元済みkeyを原子的に反映し、先頭タブの一時表示を発生させない。
- 保存keyなし・確定無効時の共通補正を先頭から末尾へ変更する。
- 選択変更、削除後の選択、0タブへの遷移を保存状態へ反映する。

**Non-Goals:**

- selected keyと描画用keyを別々に管理しない。
- selected page index、Pagerの`currentPage`、スクロール位置をselected keyの代わりに保存しない。
- selected keyを既存のアプリ内バックアップJSONへ追加しない。
- Room entity、DAO、database version、migrationを変更しない。
- BoardとThreadで異なるpending command処理を本変更だけで統一しない。

## Decisions

### 1. 既存Tabs用Preferences DataStoreへ2つのnullable keyを追加する

`SlevoPreferenceDataStores.kt`へ`selected_board_tab_key`と`selected_thread_tab_key`の`stringPreferencesKey`を追加する。`TabsLocalDataSource.kt`は各keyの`Flow<String?>`とnullable setterを公開し、`TabsLocalDataSourceImpl.kt`はnull設定時にPreferences entryを削除する。`TabsRepository.kt`は同じAPIをCoordinatorへ委譲する。

Roomへselected列を追加する案は、板・スレッドそれぞれ1つだけ存在する状態を複数のtab rowへ重複させ、database version更新とmigrationを必要とするため採用しない。`lastSelectedTabsPage`と同じDataStoreを使い、既存DI bindingは変更しない。

### 2. 保存keyは初回canonical selectionを公開する前に一度だけ読む

各Coordinatorの`bind(scope)`は、対応するselected key Flowの初回値を`first()`で取得してから初回canonical一覧をselectionへ投影する。保存Flowをruntime selectionの継続的な入力にせず、初回bootstrapだけに使う。これにより、selection保存によるDataStore再emitが現在のメモリ内選択を古い値へ戻すfeedback loopを作らない。

Boardは`reconcileCanonical`の最初のloaded transitionへ復元keyを渡し、`canonicalTabs`、`selectedKey`、`presentation`を同じ`_state.update`で確定する。Loading中に`selectedKey`だけを設定すると`rebuildPresentation()`がnullへ正規化するため行わない。

Threadは初回canonical collectで`publishProjectedTabs(requestedSelection = restoredKey)`を呼び、`Loaded`と最初の`threadPresentationState`を復元keyから構成する。板・スレッドともcanonicalとDataStoreのどちらかが未取得の間は既存Loadingを維持する。

### 3. 単一selected keyをruntimeと永続状態の正本として使う

描画用と永続用のselected keyは分離しない。復元keyが有効ならそのkeyを選択し、保存keyがnullまたは確定無効ならloaded一覧の`last()`のkeyへ補正する。一覧が空ならselected keyはnullとする。

`resolveTabPresentation`の確定無効fallbackを`tabs.first()`から`tabs.last()`へ変更し、Threadの`publishThreadPresentation`も同じ末尾規則へ揃える。これにより初回起動、保存key不整合、pending causeのないcanonical欠落でBoard/Thread Pagerと全画面タブ一覧が同じ末尾selected keyを使う。

### 4. 確定したselectionだけを直列に保存する

各Coordinatorはbootstrap完了後、確定したselected keyの変更を1本のcoroutineで順番に`TabsRepository`へ保存する。直接の既存タブ選択、Pager settle、route登録・選択完了、canonical reconciliationによる末尾補正を保存対象とする。

削除操作ではcanonical確認前の投影を永続状態として確定しない。BoardのDelete/BulkDeleteは受付時にpresentation上のselectionを先行変更するため、repository mutation成功とcanonical確認が完了した時点で最終selected keyを保存する。失敗・cancelでは削除前の保存keyを維持する。Threadも`removePending`で最終selectionが確定した後に保存する。最後のタブ削除またはloaded空一覧の確定時はDataStore entryを削除する。

selection関数ごとに独立した`launch`でDataStoreへ書く案は、短時間の連続選択で古いwriteが後から完了する可能性があるため採用しない。Coordinator内の単一writerで最新の確定状態を直列化する。

### 5. アプリ内バックアップ形式は変更しない

selected keyは同一端末の再起動・プロセス再生成を継続するUI状態として保存する。`BackupTabsJson`、`BackupDataMapper`、`PendingRestoreDataStoreWriter`にはfieldを追加しない。バックアップ復元後はselected keyを復元対象タブと組み合わせず、通常の初回読込規則で既存keyの有効性を確認して末尾へ補正する。

## Implementation Contract

- `TabsLocalDataSource`と`TabsRepository`に板・スレッドkeyのobserve/set APIを追加し、null setterはPreferences keyをremoveする。
- DataStore key名は`selected_board_tab_key`と`selected_thread_tab_key`とし、板は正規化済みboardUrl、スレッドは既存の`ThreadId.value`文字列を保存する。
- 各Coordinatorは永続keyを初回canonical loaded emission前に一度だけ取得し、継続的なDataStore Flowをselection入力にしない。
- Boardは`TabControllerState.selectedKey`、Threadは`_selectedThreadTabKey`を唯一のruntime selected keyとして維持し、永続化専用の第二selected keyを追加しない。
- `resolveTabPresentation`とThread側の確定無効補正は末尾keyを使用する。pending causeがある場合の`PendingMissing`、選択タブclose後の同位置・範囲外末尾規則は変更しない。
- 初回補正、明示選択、削除成功後selection、0タブ確定をDataStoreへ反映する。未確認のDelete/BulkDelete投影や失敗・cancel結果を最終保存値にしない。
- `BackupTabsJson`と既存backup format version、Room schema、`AppRoute`、`TabPresentationState`の公開型を変更しない。
- 追加・変更するclass/interfaceにはKDocを付け、非自明関数、復元guard、fallback、データ変換にはリポジトリ規約どおりのコメントを付ける。

## Error Cases / Compatibility

- 保存keyが一覧に存在しない場合はpending扱いにせず、末尾keyへ補正して保存値を置き換える。
- 保存keyがnullで一覧が非空の場合も末尾keyを選択・保存し、blankまたは暗黙のpage 0を表示しない。
- 一覧が空の場合はselected keyをnullにしてPreferences entryを削除する。後から最初のタブが追加された場合は、そのタブを選択して保存する。
- 板URLは既存Navigation正規化後のkeyを保存する。保存済み旧hostなどがcanonical keyと一致しない場合は末尾補正を使い、Coordinator内で別のURL正規化を行わない。
- DataStore読込完了がRoomより遅い場合もLoadingを維持し、先頭または末尾を先に表示してから復元keyへ跳ばない。
- DataStore書込に失敗しても現在プロセスの有効なselectionとpresentationは維持する。後続selection保存を停止させず、次の確定変更で再度最新値を保存する。
- 既存Preferences fileには新keyが存在しないため、アップデート直後はnullとして読み、末尾補正後に保存する。明示migrationは不要である。

## Testing Strategy

- `TabsLocalDataSourceImpl`のテストで板・スレッドkeyの初期null、保存、上書き、nullによる削除、相互独立性を検証する。
- `TabsRepository`のfake/mockを更新し、selected key APIがlocal data sourceへ正しく委譲されることを検証する。
- `BoardTabsCoordinatorTest`で有効な保存keyの初回復元、保存keyなしの末尾補正、不正keyの末尾補正、初回loaded emissionのatomicity、明示選択の保存、削除成功・失敗・最後の削除を検証する。
- `ThreadTabsCoordinatorTest`でBoardと同じ復元・末尾補正・保存シナリオに加え、pending Delete中は保存値を先行更新せずcanonical確認後に更新することを検証する。
- 共通projectionテストで確定無効selectionが先頭ではなく末尾へ補正され、pending missingとEmpty規則が変わらないことを検証する。
- `TabSessionStoreTest`で板・スレッドAPIが独立し、route登録・選択完了後に対応keyが永続化対象になることを検証する。
- 全画面Tabsの回帰テストで、初回保存keyなしは末尾、再起動相当の有効保存keyは選択カード中央、削除済み保存keyは補正後の末尾を表示することを検証する。
- 実装後にCIで`./gradlew build`と`./gradlew test`を実行し、既存backup JSON fixtureとRoom migrationテストに変更がないことを確認する。

## Migration Plan

1. Preferences keyとLocalDataSource/Repository API、テストfakeを加法的に追加する。
2. 共通selection repairを末尾fallbackへ変更し、既存先頭fallbackテストを更新する。
3. Board Coordinatorへ初回復元と確定selection保存を追加する。
4. Thread Coordinatorへ同じ復元・保存を追加し、既存pending処理を維持する。
5. TabSessionStore、Pager、全画面Tabsの復元・初期位置を回帰検証する。

ロールバック時はCoordinatorの読込・保存を除去し、補正規則を先頭へ戻した後、新しいPreferences keyを未参照のまま残す。DataStoreの未知keyは旧コードの動作へ影響せず、Roomまたはbackup migrationのロールバックは不要である。

## Risks / Trade-offs

- [DataStore読込待ちで初回タブ表示が遅延する] → 板・スレッドごとにbootstrapで一度だけ読み、既存Loading表示を維持したままcanonicalと合流する。
- [DataStore再emitがruntime selectionを古い値へ戻す] → 永続Flowは`first()`によるbootstrap入力だけに使い、以後はCoordinator stateを正本にする。
- [連続選択の非同期writeが逆順に完了する] → Coordinatorごとに単一writerで確定selectionを直列保存する。
- [未確認削除selectionが保存され、失敗後の再起動で誤ったタブを復元する] → Delete/BulkDeleteはcanonical確認後だけ最終keyを保存し、失敗・cancelでは保存値を維持する。
- [BoardとThreadの既存pending実装差が永続化タイミングをずらす] → 共通の外部契約をテストしつつ、各Coordinatorの確認完了点に個別に保存hookを置く。
- [全画面Tabsの初期スクロールがDataStore復元前のkeyを読む] → loaded presentation公開前に復元を完了し、既存`boardLoaded`/`threadLoaded` guard後は確定済みkeyだけを公開する。
