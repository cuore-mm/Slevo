## ADDED Requirements

### Requirement: スレッドタブの読込状態を明示する
システムは永続スレッドタブ一覧の状態を、初回 DB snapshot 未受信の読込中状態と、初回 snapshot 受信後の読込済み状態に区別しなければならないMUST。読込済み状態は空一覧と非空一覧の両方を正規状態として扱わなければならないMUST。

#### Scenario: 初回 Flow が停止している
- **WHEN** DB に 1,252 件のスレッドタブがあり初回 Room Flow emission がまだ届いていない
- **THEN** システムはスレッドタブを読込中として扱い、空の canonical 一覧として公開または保存しない

#### Scenario: 保存済みタブが空である
- **WHEN** 初回 Room Flow が空一覧を emit する
- **THEN** システムは読込済み空状態へ遷移し、読込中状態と区別する

#### Scenario: 保存済みタブが存在する
- **WHEN** 初回 Room Flow が非空一覧を emit する
- **THEN** システムはその DB snapshot を canonical 一覧とする

### Requirement: DB snapshot を永続タブ一覧の正本とする
システムは Room Flow が通知する DB snapshot を永続スレッドタブ一覧の canonical state としなければならないMUST。通常 mutation は canonical state を直接置換してはならずMUST NOT、未完了 operation を canonical snapshot に決定的に投影して表示状態を導出しなければならないMUST。

#### Scenario: pending add 中に古い snapshot を受信する
- **WHEN** 1,252 件の canonical 一覧への add が未確認の間に古い 1,252 件 snapshot を受信する
- **THEN** システムは pending add を再適用して対象タブを表示状態に保持し、既存 1,252 件も削除しない

#### Scenario: add 後の snapshot を受信する
- **WHEN** pending add の対象を含む 1,253 件 snapshot を Room Flow が emit する
- **THEN** システムは snapshot を canonical state として受け入れ、pending add を除去しても同じ 1,253 件を維持する

#### Scenario: pending delete 中に古い snapshot を受信する
- **WHEN** delete が未確認の間に削除対象を含む古い snapshot を受信する
- **THEN** システムは pending delete を再適用し、対象タブを一時的に復活させない

#### Scenario: pending pin 中に古い snapshot を受信する
- **WHEN** pin 更新が未確認の間に更新前の pin 値を持つ snapshot を受信する
- **THEN** システムは pending pin を再適用し、表示中の pin 値を更新前へ戻さない

### Requirement: 通常 mutation を対象タブ単位で永続化する
システムは通常のスレッドタブ追加、削除、pin 更新、タブ情報更新、スクロール位置更新を対象 `threadId` 単位の DB mutation として実行しなければならないMUST。これらの操作で一覧全体の full replacement または `deleteNotIn` を実行してはならないMUST NOT。

#### Scenario: 新しいタブを追加する
- **WHEN** 読込済み一覧へ未登録 `threadId` のタブを追加する
- **THEN** システムは既存タブ行を変更または削除せず対象行を追加し、必要な共通 ThreadState を同じ整合した write として保存する

#### Scenario: 既存タブを ensure する
- **WHEN** 既に開いている `threadId` を再度 ensure する
- **THEN** システムはそのタブの sort order、pin、scroll position を保持し、重複行を作成しない

#### Scenario: タブを削除する
- **WHEN** 対象 `threadId` のタブを閉じる
- **THEN** システムは対象の open-thread-tab 行だけを削除し、他のタブと共通 ThreadState の遅延 GC 契約を維持する

#### Scenario: pin を更新する
- **WHEN** 対象 `threadId` の pin 状態を変更する
- **THEN** システムは対象行の pin 列だけを更新し、他行と対象行の sort order、scroll position を変更しない

#### Scenario: thread info を更新する
- **WHEN** タイトル、レス数、または解決済み板情報を更新する
- **THEN** システムは対象の共通 ThreadState を更新し、open-thread-tab 一覧全体を再保存しない

### Requirement: mutation intent と完了を直列化する
システムはスレッドタブ mutation intent を受付順の単一系列で処理しなければならないMUST。各 mutation API は DB write と対応する canonical Flow confirmation の完了後に成功を返し、失敗または cancellation を呼出元へ報告しなければならないMUST。

#### Scenario: 読込前に mutation を要求する
- **WHEN** 初回 Room Flow emission 前に add、delete、または pin intent を受け付ける
- **THEN** システムは初回 canonical snapshot まで intent を待機させ、未初期化の空一覧を基に DB mutation を行わない

#### Scenario: mutation を連続要求する
- **WHEN** add、delete、pin intent が完了を待たず連続して到着する
- **THEN** システムは受付順に一件ずつ DB write と Flow confirmation を完了し、各 caller へ対応する completion を返す

#### Scenario: DB write が失敗する
- **WHEN** 対象行 mutation が例外または失敗結果で終了する
- **THEN** システムは pending operation を除去し、既存 canonical state を維持し、失敗を caller へ返して後続 intent の処理を継続する

#### Scenario: mutation 待機がキャンセルされる
- **WHEN** 未完了 mutation の caller または coordinator scope がキャンセルされる
- **THEN** システムは未完了 completion と pending operation を安全に整理し、fire-and-forget DB save を残さない

#### Scenario: readiness または write permit の待機中に caller がキャンセルされる
- **WHEN** mutation intent が初回 canonical snapshot または `DatabaseWriteGate` の write permit を待っている間に caller がキャンセルされる
- **THEN** システムは readiness 後にも caller cancellation を再確認して当該 intent の実行 context へ cancellation を伝播し、対象の Room transaction を開始せず、後続 intent の FIFO 処理を継続する

#### Scenario: Room transaction 開始後に caller がキャンセルされる
- **WHEN** 対象行 mutation の Room transaction が開始した後に caller がキャンセルされる
- **THEN** システムは cancellation を transaction の実行 context へ伝播し、cancellation が transaction の成功完了より先なら Room に rollback させ、成功完了が先ならその commit を既完了として扱い、部分 write、補償 write、再試行、caller 固有の後続 side effect を開始せず、Room Flow の最終 snapshot を canonical state として pending operation を整理する

### Requirement: 既存 DB write coordination を維持する
システムはすべての新しい Room write を既存 `DatabaseWriteGate.withWritePermit` と必要な Room transaction の内側で実行しなければならないMUST。mutation intent queue は `DatabaseWriteGate` を置換してはならずMUST NOT、同じ write を二重に gate してはならないMUST NOT。

#### Scenario: 通常 mutation と backup suspension が競合する
- **WHEN** backup export が write suspension を保持している間にタブ mutation が到着する
- **THEN** repository write は既存 gate で待機し、gate を迂回せず、suspension 解放後に transaction を実行する

#### Scenario: thread state を同じ transaction で更新する
- **WHEN** タブ追加が open-thread-tab 行と共通 ThreadState の両方を更新する
- **THEN** システムは outer write permit と一つの Room transaction を使用し、内部 helper で write permit を再取得しない

### Requirement: full replacement を明示的 bulk operation に限定する
システムは open-thread-tab 一覧の full replacement と `deleteNotIn` を明示的な bulk operation にのみ許可しなければならないMUST。bulk replacement は初回 canonical load 完了後、通常 mutation と競合しない専用 orchestration、`DatabaseWriteGate`、Room transaction の内側でのみ実行しなければならないMUST。

#### Scenario: 通常操作を実行する
- **WHEN** add、delete、pin、thread info、scroll position の通常操作を実行する
- **THEN** システムは bulk replacement API と `deleteNotIn` を呼ばない

#### Scenario: cold-start restore を適用する
- **WHEN** `PendingRestoreApplier` が `AppDatabase` 作成前に検証済み DB file を交換する
- **THEN** システムは既存の cold-start restore 契約を維持し、実行中 coordinator の full replacement として扱わない

### Requirement: 既存データとタブ固有値を維持する
システムは schema migration なしで既存 `open_thread_tabs` データを読み、通常 mutation の対象外であるタブの順序、pin、scroll position を維持しなければならないMUST。

#### Scenario: 1,252 件の既存タブから起動する
- **WHEN** DB に順序、pin、scroll position を持つ 1,252 件のタブが保存されている
- **THEN** システムは全件を同じ識別子とタブ固有値で canonical state に読み込み、追加操作で既存行を削除または再作成しない

#### Scenario: targeted mutation 後に再起動する
- **WHEN** add、delete、pin の対象行 mutation を完了してアプリを再起動する
- **THEN** システムは DB から最終確定一覧を復元し、削除済みタブを復活させず、追加済みタブを失わない
