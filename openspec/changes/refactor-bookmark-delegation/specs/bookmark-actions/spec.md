## ADDED Requirements
### Requirement: Shared bookmark actions delegation
BoardViewModel と ThreadViewModel は、共通のブックマーク操作インターフェースを Kotlin の `by` を用いたインターフェース委譲で実装し、UI からの操作をそのインターフェース経由で公開しなければならない (SHALL)。

#### Scenario: Bookmark actions invoked from shared UI
- **WHEN** ブックマークUIが保存、解除、グループ編集の操作を要求する
- **THEN** 具体型キャストなしに共通インターフェース経由で処理が実行される
