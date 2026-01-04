## ADDED Requirements
### Requirement: Shared bookmark actions delegation
BoardViewModel と ThreadViewModel は、BoardBookmarkViewModel / ThreadBookmarkViewModel が実装する共通のブックマーク操作インターフェースを Kotlin の `by` を用いたインターフェース委譲で公開しなければならない (SHALL)。また、各ブックマーク ViewModel は ViewModel 生成時に構築されなければならない (SHALL)。

#### Scenario: Bookmark actions invoked from shared UI
- **WHEN** ブックマークUIが保存、解除、グループ編集の操作を要求する
- **THEN** 具体型キャストなしに共通インターフェース経由で処理が実行される
