## MODIFIED Requirements

### Requirement: reorderと後続タブcommandの順序整合
Controllerは、reorderの受理後にadd、delete、pin、infoまたはscroll commandを受理した場合、各commandのstable keyに基づき受理順と最終存在intentを保たなければならない（SHALL）。挿入基準keyを伴うaddは、その基準keyがeffective tabsに存在する場合に直後へ投影し、基準keyが存在しない場合は末尾へ投影しなければならない（SHALL）。reorderはタブの存在、固定状態、情報、スクロール位置を変更してはならない（MUST NOT）。

#### Scenario: reorder待機中にタブを追加する
- **WHEN** pending reorderのcanonical確認前に挿入基準keyを伴わない新規タブ追加commandを受理する
- **THEN** Controllerはreorder対象の相対順を維持し、新規タブを末尾へ投影する

#### Scenario: reorder待機中に現在タブ直後へタブを追加する
- **WHEN** pending reorderのcanonical確認前にeffective tabsへ存在する挿入基準keyを伴う新規タブ追加commandを受理する
- **THEN** Controllerはreorder対象の相対順を維持し、新規タブを挿入基準keyの直後へ投影する

#### Scenario: reorder待機中にタブを削除する
- **WHEN** pending reorderのcanonical確認前に対象タブのdelete commandを受理する
- **THEN** Controllerは削除keyを投影順序から除外し、残存タブのreorder順を維持する

### Requirement: targeted persistence と explicit repository result
システムは Board／Thread の通常 add/ensure、delete、pin、metadata、scroll operation を対象行単位の suspend repository/DAO command で実行し、成功、no-op、失敗を Controller が識別できる結果を返すことを SHALL 要求する。通常操作は full-list upsert/delete replacement を呼んではならない。挿入基準keyを伴う新規Thread addだけは、挿入位置以降の`sortOrder`更新と対象行挿入を一つの明示的transactionで行うことを許可し、集合、pin、scroll、metadataを変更してはならない（MUST NOT）。

#### Scenario: Board single-row mutation
- **WHEN** 1,252 件の Board tab が保存済みの状態で一件を ensure、pin、更新、delete する
- **THEN** 対象行と必要な関連 state だけが変更され、他の行、順序、pin、scroll、metadata は不変である

#### Scenario: Thread single-row mutation
- **WHEN** 1,252 件の Thread tab が保存済みの状態で一件を位置指定なしでensure、pin、metadata更新、deleteする
- **THEN** 対象行と必要な ThreadState だけが変更され、他の行と tab 固有値は不変である

#### Scenario: Thread位置指定add
- **WHEN** 1,252件のThread tabが保存済みの状態で、既存anchor直後へ未登録Thread tabを追加する
- **THEN** 対象行の挿入と挿入位置以降の`sortOrder`だけが単一transactionで変更され、集合内の既存行、pin、scroll、metadataは不変である

#### Scenario: bulk operation の隔離
- **WHEN** 通常 UI command が処理される
- **THEN** `upsertAll` と `deleteNotIn` を組み合わせる full replacement API は呼ばれず、明示 bulk/restore 経路だけから利用可能である

## ADDED Requirements

### Requirement: スレッドリンク由来タブの位置指定ensure
Thread Controllerは、現在選択中スレッドのstable keyを挿入基準として対象スレッドをensureし、対象が新規の場合だけ基準タブの直後へ挿入しなければならない（SHALL）。対象が既存の場合は集合と順序を変更せず対象を選択しなければならない（SHALL）。登録、順序、選択は一つの整合したpresentationへ投影しなければならない（SHALL）。

#### Scenario: 未登録スレッドを現在タブ直後へ追加する
- **WHEN** 現在スレッドAが選択され、未登録のスレッドBを位置指定ensureする
- **THEN** ControllerはBをAの直後へ一度だけ投影して選択する

#### Scenario: 登録済みスレッドをensureする
- **WHEN** 対象スレッドBが既にタブとして存在する状態で位置指定ensureする
- **THEN** Controllerはタブを重複作成または並べ替えずBを選択する

#### Scenario: 挿入基準を解決できない
- **WHEN** 位置指定ensureの挿入基準keyがeffective tabsに存在しない
- **THEN** Controllerは対象が新規なら末尾へ一度だけ追加し、対象を選択する
