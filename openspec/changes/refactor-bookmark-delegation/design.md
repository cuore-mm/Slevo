## Context
BoardViewModel と ThreadViewModel は BaseViewModel のブックマーク補助関数で操作を転送しており、共通UI側は具体型キャストで呼び出している。SingleBookmarkViewModel は板/スレッドの分岐を1クラスで吸収している。

## Goals / Non-Goals
- Goals: ブックマーク操作APIを統一し、重複した転送ロジックを削減し、インターフェース経由で利用できるようにする。
- Non-Goals: ブックマークの挙動、永続化、UI状態の内容を変更しない。

## Decisions
- Decision: BookmarkActions インターフェースを定義し、BoardBookmarkViewModel と ThreadBookmarkViewModel がそれを実装する。
- Decision: SingleBookmarkViewModel を分割し、板/スレッドごとの分岐はクラス分割で吸収する。
- Decision: BoardViewModel と ThreadViewModel は ViewModel 生成時に対応するブックマーク ViewModel を構築し、Kotlin の `by` でインターフェース委譲を公開する。
- Decision: BoardViewModelFactory/ThreadViewModelFactory に BoardInfo/ThreadInfo を渡せるようにし、TabViewModelRegistry から生成時に供給する。
- Decision: BbsRouteScaffold は具体型キャストではなく共有インターフェース経由で呼び出す。

## Risks / Trade-offs
- Risk: ViewModel 生成時に必要な BoardInfo/ThreadInfo が不足する可能性がある。対策: TabViewModelRegistry で生成前に必要情報を組み立て、欠落時は生成を遅延させる。
- Risk: 板/スレッド間で共通処理が分散し、重複が増える可能性がある。対策: 共通ロジックは共通ヘルパーまたは小さな委譲クラスへ集約する。
- Risk: 既存APIの変更で呼び出し側が崩れる可能性がある。対策: 既存のメソッド名とシグネチャを維持する。

## Migration Plan
- BookmarkActions インターフェースを追加し、BoardBookmarkViewModel/ThreadBookmarkViewModel に実装する。
- SingleBookmarkViewModel を BoardBookmarkViewModel/ThreadBookmarkViewModel に分割する。
- BoardViewModel と ThreadViewModel の生成時に対応するブックマーク ViewModel を構築する。
- ViewModel の生成シグネチャ変更に合わせて TabViewModelRegistry と呼び出し元を更新する。
- BbsRouteScaffold の呼び出しを共有インターフェースへ置き換える。
- build と unit test で検証する。

## Open Questions
- なし。
