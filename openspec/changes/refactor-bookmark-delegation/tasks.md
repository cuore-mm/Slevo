## 1. Implementation
- [ ] 1.1 BookmarkActions インターフェースを追加し、SingleBookmarkViewModel に実装する。
- [ ] 1.2 BoardViewModel/ThreadViewModel のファクトリが BoardInfo/ThreadInfo を受け取れるように更新する。
- [ ] 1.3 BoardViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.4 ThreadViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.5 BbsRouteScaffold のブックマーク操作呼び出しを共有インターフェースに置き換える。
- [ ] 1.6 BaseViewModel のブックマーク転送ヘルパーを未使用になったら整理する。

## 2. Validation
- [ ] 2.1 `./gradlew build` を実行する。
- [ ] 2.2 `./gradlew test`（または `./gradlew testDebugUnitTest`）を実行する。
