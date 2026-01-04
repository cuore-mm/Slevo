## 1. Implementation
- [ ] 1.1 BookmarkActions インターフェースを追加し、BoardBookmarkViewModel/ThreadBookmarkViewModel に実装する。
- [ ] 1.2 SingleBookmarkViewModel を BoardBookmarkViewModel と ThreadBookmarkViewModel に分割する（共通ロジックは共通ヘルパーに集約する）。
- [ ] 1.3 共通ヘルパー（グループ編集・ダイアログ制御）を追加し、両 ViewModel から合成して利用する。
- [ ] 1.4 BoardViewModel/ThreadViewModel のファクトリが BoardInfo/ThreadInfo を受け取れるように更新する。
- [ ] 1.5 BoardViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.6 ThreadViewModel に委譲の初期化と Kotlin の `by` を使ったインターフェース委譲を組み込む。
- [ ] 1.7 BbsRouteScaffold のブックマーク操作呼び出しを共有インターフェースに置き換える。
- [ ] 1.8 BaseViewModel のブックマーク転送ヘルパーを未使用になったら整理する。

## 2. Validation
- [ ] 2.1 `./gradlew build` を実行する。
- [ ] 2.2 `./gradlew test`（または `./gradlew testDebugUnitTest`）を実行する。
