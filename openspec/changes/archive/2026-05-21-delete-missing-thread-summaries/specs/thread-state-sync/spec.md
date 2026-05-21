## ADDED Requirements

### Requirement: Summary 削除後の独立データ保持
システムは subject.txt から消えたスレッドの `thread_summaries` 行を削除しても、履歴、ブックマーク、開いているスレッドタブ、共通客観状態を summary 削除に連動して削除してはならないMUST NOT。これらのデータはそれぞれのテーブルの保持規則に従って管理されなければならないMUST。

#### Scenario: 履歴があるスレッドの summary を削除する
- **WHEN** subject.txt から消えたスレッドに閲覧履歴が存在する
- **THEN** システムは `thread_summaries` の行だけを削除し、閲覧履歴を保持する

#### Scenario: ブックマークがあるスレッドの summary を削除する
- **WHEN** subject.txt から消えたスレッドにブックマークが存在する
- **THEN** システムは `thread_summaries` の行だけを削除し、ブックマークを保持する

#### Scenario: 開いているタブがあるスレッドの summary を削除する
- **WHEN** subject.txt から消えたスレッドが開いているスレッドタブに存在する
- **THEN** システムは `thread_summaries` の行だけを削除し、開いているタブとタブ固有状態を保持する

#### Scenario: 共通客観状態があるスレッドの summary を削除する
- **WHEN** subject.txt から消えたスレッドに共通客観状態が存在する
- **THEN** システムは `thread_summaries` の行だけを削除し、共通客観状態は既存の遅延 GC 規則に従って保持または削除する

### Requirement: 板更新後の共通客観状態同期継続
システムは summary 削除方式へ変更しても、最新 subject.txt に存在するスレッドの共通客観状態を更新しなければならないMUST。subject.txt から消えた summary の削除は、subject.txt に残るスレッドの共通客観状態同期を阻害してはならないMUST NOT。

#### Scenario: 一部 summary を削除しながら現役スレッドを同期する
- **WHEN** 板更新で一部スレッドが subject.txt から消え、別のスレッドは subject.txt に残っている
- **THEN** システムは消えた summary を削除し、残っているスレッドの共通客観状態を更新する
