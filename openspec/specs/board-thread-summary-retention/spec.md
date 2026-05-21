# board-thread-summary-retention Specification

## Purpose
板一覧キャッシュ `thread_summaries` の保持ルールを定義する。現役スレッドだけを保持し、更新時の削除と再出現時の扱いを統一する。

## Requirements
### Requirement: 板一覧キャッシュの現役スレッド限定保持
システムは `thread_summaries` を最新 subject.txt に存在する現役スレッドのキャッシュとして保持しなければならない（MUST）。板更新時に最新 subject.txt から消えた summary は、アーカイブ状態へ更新せず削除しなければならない（MUST）。`thread_summaries` はアーカイブ状態を保持してはならない（MUST NOT）。

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
システムは一度 subject.txt から消えて削除されたスレッドが後の subject.txt に再出現した場合、新規 summary として扱わなければならない（MUST）。再出現時の `firstSeenAt` は再挿入時点を表さなければならない（MUST）。

#### Scenario: 削除済みスレッドが再出現する
- **WHEN** 過去に subject.txt から消えて summary が削除されたスレッドが最新 subject.txt に存在する
- **THEN** システムは当該スレッドを新規 summary として挿入し、再挿入時点の `firstSeenAt` を保存する
