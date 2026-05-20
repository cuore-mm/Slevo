## ADDED Requirements

### Requirement: 板一覧キャッシュの現役スレッド限定保持
システムは `thread_summaries` を最新 subject.txt に存在する現役スレッドのキャッシュとして保持しなければならないMUST。板更新時に最新 subject.txt から消えた summary は、アーカイブ状態へ更新せず削除しなければならないMUST。

#### Scenario: 現役 summary が subject.txt から消える
- **WHEN** 更新開始時点で `thread_summaries` に存在する現役スレッドが最新 subject.txt に存在しない
- **THEN** システムは当該 summary を `thread_summaries` から削除する

#### Scenario: subject.txt に残る summary を更新する
- **WHEN** 最新 subject.txt に既存 summary と同じ板内 thread key が存在する
- **THEN** システムは当該 summary のタイトル、レス数、表示順位を更新し、削除しない

#### Scenario: subject.txt に新しいスレッドが存在する
- **WHEN** 最新 subject.txt に `thread_summaries` に存在しない板内 thread key が存在する
- **THEN** システムは当該スレッドを新規 summary として挿入する

### Requirement: 削除方式での再出現スレッド扱い
システムは一度 subject.txt から消えて削除されたスレッドが後の subject.txt に再出現した場合、新規 summary として扱わなければならないMUST。再出現時の `firstSeenAt` は再挿入時点を表さなければならないMUST。

#### Scenario: 削除済みスレッドが再出現する
- **WHEN** 過去に subject.txt から消えて summary が削除されたスレッドが最新 subject.txt に存在する
- **THEN** システムは当該スレッドを新規 summary として挿入し、再挿入時点の `firstSeenAt` を保存する

### Requirement: 大量削除対象の安全な削除
システムは板更新時に削除対象 summary ID が大量に存在しても、SQLite の SQL 変数上限を超えない単位で削除しなければならないMUST。分割された削除は同じ板更新トランザクション内で扱われ、失敗時には板更新全体が不完全な状態で確定してはならないMUST NOT。

#### Scenario: 削除対象が SQLite 変数上限を超える件数である
- **WHEN** 最新 subject.txt から消えた summary ID が 1 回の `IN` 句で扱える件数を超える
- **THEN** システムは複数回の安全な削除単位に分割して全対象を削除する

#### Scenario: 分割削除中に失敗する
- **WHEN** summary の分割削除中に DB 更新エラーが発生する
- **THEN** システムは板更新トランザクションを確定せず、部分的なキャッシュ更新を残さない

### Requirement: 既存アーカイブ済み summary の整理
システムは既存 DB に残る `isArchived = 1` の `thread_summaries` 行を削除対象として扱わなければならないMUST。整理処理は `isArchived = 0` の現役 summary を削除してはならないMUST NOT。

#### Scenario: 既存 DB にアーカイブ済み summary が残っている
- **WHEN** アプリ更新後の DB に `isArchived = 1` の `thread_summaries` 行が存在する
- **THEN** システムは migration または cleanup により当該行を削除対象にする

#### Scenario: 現役 summary が存在する
- **WHEN** `thread_summaries` に `isArchived = 0` の行が存在する
- **THEN** システムは既存アーカイブ行の整理処理で当該行を削除しない
