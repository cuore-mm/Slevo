## Context
BoardViewModel と ThreadViewModel は BaseViewModel のブックマーク補助関数で操作を転送しており、共通UI側は具体型キャストで呼び出している。

## Goals / Non-Goals
- Goals: ブックマーク操作APIを統一し、重複した転送ロジックを削減し、インターフェース経由で利用できるようにする。
- Non-Goals: ブックマークの挙動、永続化、UI状態の内容を変更しない。

## Decisions
- Decision: SingleBookmarkViewModel を包む BookmarkActions インターフェースと委譲実装を定義する。
- Decision: BoardViewModel と ThreadViewModel は初期化時に委譲実装を構築し、インターフェース委譲で公開する。
- Decision: BbsRouteScaffold は具体型キャストではなく共有インターフェース経由で呼び出す。

## Risks / Trade-offs
- Risk: 委譲の初期化順序により呼び出しが早すぎる可能性がある。対策: 既存の initializeBoard/initializeThread の流れを維持し、未初期化時のガードを置く。
- Risk: 既存APIの変更で呼び出し側が崩れる可能性がある。対策: 既存のメソッド名とシグネチャを維持する。

## Migration Plan
- インターフェースと委譲ラッパを追加する。
- BoardViewModel と ThreadViewModel に委譲を組み込む。
- BbsRouteScaffold の呼び出しを共有インターフェースへ置き換える。
- build と unit test で検証する。

## Open Questions
- なし。
