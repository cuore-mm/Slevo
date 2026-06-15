## ADDED Requirements

### Requirement: Route 単位の ViewModel 所有
システムは板画面とスレッド画面の ViewModel を、開いているタブ単位ではなく画面 route 単位で所有しなければならないMUST。Pager の各タブページは独自の `BoardViewModel` または `ThreadViewModel` インスタンスを要求してはならないMUST NOT。

#### Scenario: 複数スレッドタブを表示する
- **WHEN** ユーザーが複数のスレッドタブを開き、Pager でタブを切り替える
- **THEN** システムはスレッドタブ数に比例して `ThreadViewModel` を生成せず、スレッド画面 route の ViewModel が選択中タブの状態を合成する

#### Scenario: 複数板タブを表示する
- **WHEN** ユーザーが複数の板タブを開き、Pager でタブを切り替える
- **THEN** システムは板タブ数に比例して `BoardViewModel` を生成せず、板画面 route の ViewModel が選択中タブの状態を合成する

### Requirement: タブセッション状態の正本管理
システムはタブの並び順、選択状態、ピン留め、スクロール位置、検索クエリ、表示モード、ポップアップスタック、投稿ダイアログ下書きなどのタブ固有状態を、ViewModel インスタンスではなくタブセッション状態として管理しなければならないMUST。ViewModel はこれらの状態を長期保持する正本になってはならないMUST NOT。

#### Scenario: タブを切り替えて戻る
- **WHEN** ユーザーがスレッドタブ A で検索条件またはポップアップ状態を変更し、スレッドタブ B へ切り替えた後にタブ A へ戻る
- **THEN** システムはタブ A のタブセッション状態から検索条件またはポップアップ状態を復元する

#### Scenario: ViewModel が再作成される
- **WHEN** 構成変更などにより route 単位の ViewModel が再作成される
- **THEN** システムはタブセッション状態と永続データから表示状態を再合成し、タブごとのセッション状態を ViewModel インスタンス消失で失わない

### Requirement: 表示データ正本と UiState 合成の分離
システムは板一覧、スレ本文、パース済み投稿、既読状態、ブックマーク、NG 設定を Repository、DB、または UseCase の正本から取得しなければならないMUST。ViewModel はそれらの正本とタブセッション状態を合成した表示用 `UiState` を公開し、表示データの長期正本を保持してはならないMUST NOT。

#### Scenario: スレッド本文を表示する
- **WHEN** スレッド画面 route の ViewModel が選択中スレッドタブを表示する
- **THEN** システムは Repository または UseCase から取得したスレ本文とタブセッション状態を合成して `ThreadUiState` を生成する

#### Scenario: 板一覧を表示する
- **WHEN** 板画面 route の ViewModel が選択中板タブを表示する
- **THEN** システムは Repository または UseCase から取得した板スレ一覧とタブセッション状態を合成して `BoardUiState` を生成する

### Requirement: Pager composition 範囲に基づく UiState 購読
システムは Pager の全タブ分の完全な `UiState` を常時合成してはならないMUST NOT。Pager が composition しているページだけが対象タブ key の `UiState` を購読し、composition から外れたページの重い合成処理は購読停止により停止できなければならないMUST。

#### Scenario: Pager が隣接ページを compose する
- **WHEN** Compose Pager が表示中ページまたは offscreen page を composition に含める
- **THEN** システムはそのページの tab key に対応する `UiState` Flow だけを購読する

#### Scenario: Pager がページを composition から外す
- **WHEN** Compose Pager が非表示ページを composition から外す
- **THEN** システムは対象 tab key の `UiState` 購読を停止し、Repository cache やタブセッション状態を除く重い表示合成を継続しない

#### Scenario: 多数のタブを開く
- **WHEN** ユーザーが多数の板タブまたはスレッドタブを開く
- **THEN** システムは開いている全タブ分の完全な `UiState` を常時 combine せず、Pager が必要とするページ範囲に合成対象を限定する

### Requirement: 独自 ViewModel registry 依存の削減
システムはタブ単位の ViewModel キャッシュをアプリ状態の正本として使用してはならないMUST NOT。既存互換のために registry を一時的に残す場合でも、タブ削除や画面破棄の正しい挙動はタブセッション状態と Android 標準の ViewModel ライフサイクルで表現されなければならないMUST。

#### Scenario: タブを閉じる
- **WHEN** ユーザーが板タブまたはスレッドタブを閉じる
- **THEN** システムは対象タブのタブセッション状態を削除し、ViewModel registry の手動 release を表示状態維持の必須条件にしない

#### Scenario: 画面 route が破棄される
- **WHEN** 板画面またはスレッド画面の route が navigation back stack から破棄される
- **THEN** システムは Android 標準の ViewModel ライフサイクルに従って route 単位 ViewModel の監視ジョブを終了する

### Requirement: タブ切替時の表示体験維持
システムは route 単位 ViewModel へ移行した後も、タブ切替、スクロール位置復元、新着表示、検索、ポップアップ、投稿ダイアログ、更新操作のユーザー体験を維持しなければならないMUST。

#### Scenario: スクロール位置を復元する
- **WHEN** ユーザーが別タブへ切り替えた後に元のタブへ戻る
- **THEN** システムは対象タブの保存済みスクロール位置を使用して表示位置を復元する

#### Scenario: 更新操作を行う
- **WHEN** ユーザーが表示中の板タブまたはスレッドタブで更新操作を行う
- **THEN** システムは対象タブ key に対応するデータを更新し、更新結果をタブセッション状態と `UiState` に反映する
