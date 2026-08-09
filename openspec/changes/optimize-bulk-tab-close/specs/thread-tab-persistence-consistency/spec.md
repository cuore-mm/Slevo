## ADDED Requirements

### Requirement: Thread一括クローズのchunk化永続化
ThreadタブRepositoryは、一括クローズ対象IDを最大900件のchunkへ分割し、1回の `DatabaseWriteGate` write permitと1つのRoom transaction内で対象行だけを削除しなければならない（SHALL）。

#### Scenario: 複数chunkを1 transactionで削除する
- **WHEN** Thread一括クローズ対象が900件を超える
- **THEN** Repositoryは複数の対象ID DELETEを同じRoom transaction内で実行し、transaction commitを1回だけ行う

#### Scenario: 残存行を変更しない
- **WHEN** Thread一括クローズを永続化する
- **THEN** Repositoryは対象外のopen thread tab、sortOrder、pin、scroll、ThreadState timestampを変更しない

#### Scenario: full replacementを使用しない
- **WHEN** Thread一括クローズを永続化する
- **THEN** Repositoryは `replaceOpenThreadTabsForBulkOperation`、`deleteNotIn`、残存行upsertを呼び出さない

### Requirement: Thread一括クローズの遅延GCを1回実行する
ThreadタブRepositoryは、1件以上の対象行を削除したbulk transactionの末尾で `collectGarbageUngated` を正確に1回実行しなければならない（SHALL）。30日TTLと1回最大100件の既存遅延GC契約を維持しなければならない（MUST）。

#### Scenario: 複数Threadタブを削除する
- **WHEN** 1回のbulk操作で複数のThreadタブ行を削除する
- **THEN** Repositoryは全chunk DELETE後にThreadState GCを1回だけ実行する

#### Scenario: 削除行がない
- **WHEN** bulk対象が空または全対象行が既に不在である
- **THEN** RepositoryはThreadState GCを実行しない

#### Scenario: GCが失敗する
- **WHEN** bulk transaction末尾のThreadState GCが失敗する
- **THEN** Repositoryは全対象行DELETEとGCを同じtransactionとしてrollbackする
