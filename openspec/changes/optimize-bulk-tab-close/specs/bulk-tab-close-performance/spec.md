## ADDED Requirements

### Requirement: 一括クローズを単一bulk mutationとして投影する
システムは、表示中ページの一括クローズ対象を1つのbulk commandおよび1つのpending projection operationとして受理しなければならない（SHALL）。対象件数分の単体close commandを登録してはならない（MUST NOT）。

#### Scenario: 大量の板タブを一度に投影から除外する
- **WHEN** 板ページの複数の未固定タブに対して一括クローズを受理する
- **THEN** システムは対象を1回のprojection更新で一覧から除外し、固定板タブとすべてのスレッドタブを維持する

#### Scenario: 大量のスレッドタブを一度に投影から除外する
- **WHEN** スレッドページの複数の未固定タブに対して一括クローズを受理する
- **THEN** システムは対象を1回のprojection更新で一覧から除外し、固定スレッドタブとすべての板タブを維持する

#### Scenario: 対象が存在しない
- **WHEN** 表示中ページに未固定タブが存在しない
- **THEN** システムはbulk command、永続化、GC、holder破棄を実行しない

### Requirement: 一括クローズ後の選択を逐次closeと一致させる
システムは、受理時点の一覧と対象順序に既存の単体close選択補正を順番に適用した結果と同じ最終選択を公開しなければならない（SHALL）。

#### Scenario: 選択中タブが対象外である
- **WHEN** 選択中タブが固定済みまたは一括対象外である
- **THEN** システムはその選択keyを維持する

#### Scenario: 選択中タブが対象で残存タブがある
- **WHEN** 選択中タブが一括対象に含まれ、同じページに残存タブがある
- **THEN** システムは一覧順の単体close反復と同じ残存タブを選択する

#### Scenario: 全タブが削除対象である
- **WHEN** 表示中ページの全タブが未固定で一括対象になる
- **THEN** システムは最終選択をnullにし、Empty presentationへ収束する

### Requirement: 一括クローズ対象を原子的に永続化する
システムは、bulk対象IDを最大900件のchunkへ分割し、全chunkを1つのwrite permitおよび1つのRoom transaction内で対象行DELETEしなければならない（SHALL）。full replacement、`deleteNotIn`、残存行upsertを使用してはならない（MUST NOT）。

#### Scenario: 900件を超える対象を削除する
- **WHEN** 一括クローズ対象が900件を超える
- **THEN** システムは各SQLのbind件数を900以下に分割し、すべてのchunkを同じRoom transactionでcommitする

#### Scenario: transactionが失敗する
- **WHEN** いずれかのchunk DELETEまたはGCが例外・cancellationで失敗する
- **THEN** システムはbulk操作全体をrollbackし、canonical一覧を再投影する

#### Scenario: 対象の一部が既に不在である
- **WHEN** bulk対象IDの一部または全部がDBに存在しない
- **THEN** システムは存在する対象だけを削除し、全対象不在のcanonical状態へNoOpを含めて収束する

### Requirement: 対象holderをbulk段階で破棄する
システムは、bulk対象の既存session holderを1つのbulk破棄段階でmapから除去し、各holderを正確に1回disposeしなければならない（SHALL）。対象外holderを破棄したり未生成holderを生成したりしてはならない（MUST NOT）。

#### Scenario: 固定holderを残す
- **WHEN** 一括対象と同じページに固定タブのholderが存在する
- **THEN** システムは対象holderだけをdisposeし、固定holderをmapに維持する

#### Scenario: 同じ対象が重複する
- **WHEN** 同じkeyがbulk入力へ重複して含まれる
- **THEN** システムはkeyを一度だけ削除し、holderを一度だけdisposeする

#### Scenario: Store lifetimeが終了する
- **WHEN** bulk処理中にActivity-retained Storeのlifetimeが終了する
- **THEN** システムはin-flight処理をcancelしてtransactionをrollbackし、残存holderを既存close処理で破棄する
