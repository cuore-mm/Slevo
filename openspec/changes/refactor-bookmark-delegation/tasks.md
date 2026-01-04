## 1. Implementation
- [ ] 1.1 BookmarkActions インターフェースを追加し、BoardBookmarkViewModel/ThreadBookmarkViewModel に実装する。
- [ ] 1.2 SingleBookmarkViewModel を BoardBookmarkViewModel と ThreadBookmarkViewModel に分割する（共通ロジックは共有ヘルパーに集約する）。
- [ ] 1.3 BoardViewModel/ThreadViewModel のファクトリが BoardInfo/ThreadInfo を受け取れるように更新する。
- [ ] 1.4 BoardViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.5 ThreadViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.6 BbsRouteScaffold のブックマーク操作呼び出しを共有インターフェースに置き換える。
- [ ] 1.7 BaseViewModel のブックマーク転送ヘルパーを未使用になったら整理する。

## 2. Validation
- [ ] 2.1 `./gradlew build` を実行する。
- [ ] 2.2 `./gradlew test`（または `./gradlew testDebugUnitTest`）を実行する。
