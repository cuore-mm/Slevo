## 1. 新着境界計算の統一

- [x] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/data/util/ThreadNewResCalculatorTest.kt` を更新し、履歴なしは0、履歴ありは `latestResCount - lastReadResNo`、全件既読・最新数減少は0、`firstNewResNo` が `null` または既読位置の次と異なる場合も `lastReadResNo` 基準になることをテストで固定する。
- [x] 1.2 `app/src/main/java/com/websarva/wings/android/slevo/data/util/ThreadNewResCalculator.kt` の `calculate` を `lastReadResNo` 基準へ変更し、1.1のテストを通す。`ThreadReadState` の保存フィールドとRoomスキーマを変更していないことを差分で確認する。
- [x] 1.3 `app/src/test/java/com/websarva/wings/android/slevo/ui/board/viewmodel/BoardThreadListTransformUseCaseTest.kt` へ、`firstNewResNo=null` および `firstNewResNo != lastReadResNo + 1` でも最新数と最終既読位置から新着件数を算出するケースを追加し、板一覧が共通計算規則を使用することを確認する。

## 2. 初回未読境界の画面セッション取り込み

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadRouteViewModel.kt` の `ThreadRouteContentState` に初回未読開始レス番号のnullableフィールドを追加し、用途と画面セッション中に固定する制約をKDocまたは非自明処理のコメントで明示する。
- [x] 2.2 同ファイルの `initializeTabMetadata` で、`tab.newResCount > 0` の場合だけ `tab.lastReadResNo + 1` を初回未読開始位置として、初回ロード開始前に保存する。既存グループを持つ状態やRoom Flow再通知で値を上書きしないガードを実装し、未訪問・全件既読では `null` になることをコードで確認する。
- [x] 2.3 `setNewArrivalInfo`、`updateThreadLastRead`、`ThreadScaffold` の既存既読更新経路が2.1のフィールドを書き換えないことを確認し、必要な場合だけ引数・copy処理を整理する。完了条件は、表示後の `lastReadResNo` 更新と初回境界の状態所有が分離していることである。

## 3. 初回レスグループの分割

- [x] 3.1 `ThreadRouteViewModel.applyLoadSuccess` から `updatePostGroups` へ初回未読開始位置を渡し、初回ロード、取得数減少、追加更新、同数更新の判定を別分岐として読める形にする。
- [x] 3.2 `updatePostGroups` の初回ロード分岐で、境界が `2..取得数` なら既読 `1..境界-1` と未読 `境界..取得数` を作り、未読側indexを `latestArrivalGroupIndex` に設定する。境界が1なら全体を単一の未読グループとしてindex 0を設定し、空グループを作らない。
- [x] 3.3 `updatePostGroups` で境界が `null`、0以下、取得数超過、または取得数0なら従来の初期グループまたは空状態を返し、`latestArrivalGroupIndex=null` にする。取得数減少による回復リセットには初回境界を再適用しないことを確認する。
- [x] 3.4 追加更新では従来どおり末尾グループを追加して最新indexを設定し、同数更新ではグループを保持して `latestArrivalGroupIndex=null` にする。`ThreadVisiblePostsUseCase.kt`、`ThreadDisplayTransformers.kt`、`ThreadPostListContent.kt`、`NewArrivalBar.kt` を変更せず既存表示経路を利用できることを差分で確認する。

## 4. ViewModelと表示変換の回帰テスト

- [x] 4.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadRouteViewModelTest.kt` に `lastReadResNo=100`、取得110件の初回ロードケースを追加し、グループが `1..100` と `101..110` に分割され、未読側が `latestArrivalGroupIndex`、101番の表示位置が `firstAfterIndex` になることを確認する。
- [x] 4.2 同テストへ `firstNewResNo=null`、履歴なし、全件既読、境界が取得数超過、`lastReadResNo=0` かつ履歴由来新着ありの各ケースを追加し、仕様どおりのグループ数とバー有無を確認する。
- [x] 4.3 同テストで初回表示後に `updateThreadLastRead` または `openThreadTabs` Flowの既読状態を進めても、初回グループ境界と `firstAfterIndex` が変わらないことを確認する。
- [x] 4.4 同テストで初回未読グループの後に追加レスをロードすると新しい末尾グループへ `latestArrivalGroupIndex` が移り、追加0件のロードでは `latestArrivalGroupIndex=null` と `firstAfterIndex=-1` になることを確認する。
- [x] 4.5 `app/src/test/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadVisiblePostsUseCaseTest.kt` と `ThreadDisplayTransformersTest.kt` に、分割済み既読・未読グループをNUMBER/TREE、検索、NGフィルターへ渡すケースを追加し、未読グループ先頭の `firstAfterIndex`、TREE文脈親、表示行の安定キー一意性を確認する。
- [x] 4.6 `ThreadPostListContent.kt` の既存条件 `firstAfterIndex != -1` かつ対象indexでのみ `NewArrivalBar` を描画すること、および `NewArrivalBar.kt` の文言・色・セマンティクスに差分がないことをレビューする。UIコンポーネント自体を変更した場合に限り、`app/src/androidTest` へ新着バーが対象投稿の直前に1つだけ表示されるComposeテストを追加する。

## 5. 検証

- [x] 5.1 `./gradlew testDebugUnitTest` を実行し、追加した境界・グループ・NUMBER/TREE・検索・NGテストを含む全単体テストが成功することを確認する。GitHub Actions `Android CI` run `30745976095` で成功した。
- [x] 5.2 `./gradlew assembleDebug` を実行し、Debugビルドが成功することを確認する。GitHub Actions `Android CI` run `30745976095` で成功した。
- [x] 5.3 DAO、Room結合、またはinstrumented test対象コードに差分が生じた場合は、関連する `TabsRepositoryThreadStateTest` を含む `./gradlew connectedDebugAndroidTest` を実行する。今回の差分はDAO、Room結合、instrumented test対象コードを含まないため、追加実行は不要と確認した。
- [x] 5.4 最終差分を確認し、DBスキーマ、ナビゲーション引数、新着バーの文言・見た目・操作・アクセシビリティ、既存の追加更新規則に意図しない変更がないことを確認する。

## 6. Codex P2 空レス回復修正

- [x] 6.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadRouteViewModel.kt` の `applyLoadSuccess` で読み込み前の `previous.posts == null` を真の初回ロード判定として `updatePostGroups` / `updateThreadPostGroups` へ渡し、初回境界を適用する条件をレス数・グループ空判定から分離する。
- [x] 6.2 同ファイルの `updateThreadPostGroups` で、初回成功後に `previousResCount == 0` または `previousGroups.isEmpty()` となった非0件取得を回復リセットとして単一グループへ戻し、`latestArrivalGroupIndex=null` とする。非0件の取得数減少も同じバーなし回復結果を維持し、追加更新・同数更新の分岐を変更しない。
- [x] 6.3 `app/src/test/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadRouteViewModelTest.kt` に、真の初回非0件で境界を適用するケース、非0件→0件→非0件および初回0件→非0件で境界を再適用しないケース、非0件の取得数減少ケースを追加する。回復後は単一グループ、`latestArrivalGroupIndex=null`、`firstAfterIndex=-1` を確認する。
- [ ] 6.4 既存の追加更新、同数更新、NUMBER/TREE、検索・NGのテストを維持し、`./gradlew testDebugUnitTest` と `./gradlew assembleDebug` をCIで成功させる。`ThreadPostListContent.kt`、`NewArrivalBar.kt`、リソース、アクセシビリティ関連ファイルに差分がないことを確認する。
