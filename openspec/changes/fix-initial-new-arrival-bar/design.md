## Context

板一覧とタブ一覧の新着件数は、`ThreadReadState.lastReadResNo` などの履歴既読状態と `thread_states.latestResCount` から `ThreadNewResCalculator` が導出する。一方、スレッド画面の `NewArrivalBar` は `ThreadRouteViewModel.updatePostGroups` が作る `ThreadPostGroup` と `latestArrivalGroupIndex` から `ThreadVisiblePostsUseCase` が算出する `firstAfterIndex` だけで表示される。

現状の初回ロードでは、保存済み状態が `lastReadResNo=100`、`firstNewResNo=101`、取得レス数110でも、`updatePostGroups` のリセット分岐が `1..110` の単一グループと `latestArrivalGroupIndex=null` を生成する。このため `firstAfterIndex=-1` となり、板一覧に新着件数があるのに101番レスの前へ新着バーが表示されない。`ThreadTabInfo.lastReadResNo` は Room から合成済みだが、`ThreadRouteContentState` と初回グループ構築には渡されていない。

制約は次のとおりである。

- 「新着バーは最新グループ先頭にのみ表示する」という既存契約を維持する。
- NUMBER/TREE、検索、NGフィルター、ツリーの文脈親、安定キー生成を変更しない。
- 初回表示境界は画面セッション開始時のスナップショットとし、スクロールによる既読位置更新では動かさない。
- 新着バーの見た目、文言、操作、アクセシビリティ挙動を変更しない。
- Room スキーマおよび保存済み `ThreadReadState` の形式を変更しない。

## Goals / Non-Goals

**Goals:**

- 履歴があり未読レスが存在するスレッドの初回ロードで、`lastReadResNo + 1` を先頭とする未読グループを復元する。
- 復元した未読グループを `latestArrivalGroupIndex` で示し、既存表示経路によって新着バーを先頭へ表示する。
- 初回ロード後の既読位置更新から表示境界を切り離し、タブを閉じるかコンテンツグループが更新されるまで画面セッション状態として保持する。
- 板一覧とタブ一覧の新着件数を `lastReadResNo` 基準へ統一し、初回表示境界と同じ規則にする。
- 後続の追加更新と新着0件更新の既存挙動を維持する。

**Non-Goals:**

- `firstNewResNo` を表示変換へ直接渡す別系統のバー判定を追加しない。
- 保存済みレス本文や `ThreadPostGroup` を永続化しない。
- 新着バーを複数表示しない。
- スクロール位置、タブ永続化、レス取得、NG判定、ソート方式を再設計しない。
- UIの外観、文言、アイコン、操作、セマンティクスを変更しない。

## Decisions

### 1. 初回未読境界を `lastReadResNo + 1` に統一する

履歴が存在し、新着件数が1件以上である場合、初回未読開始レス番号を `lastReadResNo + 1` とする。`firstNewResNo` は既存DBとの互換性のため保持するが、板一覧・タブ一覧の新着件数と初回グループ境界の計算元にはしない。新着件数は履歴がある場合に `max(latestResCount - lastReadResNo, 0)`、履歴がない場合に0とする。

これにより、`firstNewResNo=null` でも未読境界を復元でき、板一覧の件数とスレッド内で新着扱いになる範囲が一致する。

代替案として `firstNewResNo` を `ThreadVisiblePostsUseCase` へ直接渡す方法は採用しない。グループ境界とバー境界が別管理になり、NUMBER/TREE、検索、NGフィルターおよび後続更新で整合性を失うためである。

### 2. 境界は `ThreadRouteContentState` に画面セッションのスナップショットとして保持する

`ThreadRouteViewModel.initializeTabMetadata` で `ThreadTabInfo` から、履歴の有無を反映済みの `newResCount` と `lastReadResNo` を読み取る。`newResCount > 0` の場合だけ `lastReadResNo + 1` を初回未読開始位置として `ThreadRouteContentState` に保存し、それ以外は `null` とする。

この値は初回ロード開始前に一度だけ設定する。`ThreadScaffold` の `onLastRead`、`ThreadTabCoordinator.updateThreadLastRead`、Room Flow の再通知、および `setNewArrivalInfo` では更新しない。これにより、表示後のスクロールで永続既読位置が進んでもバー位置は変化しない。タブ終了時には既存の content state 破棄に従って消える。

`ThreadTabInfo` はすでに `lastReadResNo` と `newResCount` を持つため、DAO、Room entity、ナビゲーション引数は変更しない。

### 3. 初回ロードのリセット分岐だけを既読・未読グループへ分割する

`ThreadRouteViewModel.updatePostGroups` に初回未読開始位置を渡し、現在一括になっているリセット条件を次のように区別する。

1. 初回ロード（`previousResCount == 0` または `previousGroups.isEmpty()`）
2. ロード済みレス数より取得レス数が減った回復リセット
3. 取得レス数が増えた通常の追加更新
4. 取得レス数が変わらない更新

初回ロードでは次の状態を生成する。

| 条件 | `postGroups` | `latestArrivalGroupIndex` |
|---|---|---|
| 取得0件 | 空 | `null` |
| 未読開始位置が `1` | 未読 `1..N` の1グループ | `0` |
| 未読開始位置が `2..N` | 既読 `1..start-1`、未読 `start..N` | 未読側のindex |
| 未読開始位置が `null`、0以下、または `N` 超過 | 従来どおり `1..N` の初期グループ | `null` |

未読グループの `prevResCount` は `start - 1` とする。これにより `ThreadVisiblePostsUseCase.buildGroupedDisplayPosts`、`buildGroupDisplayPosts`、`buildOrderedPosts` を変更せず、既存のグループ先頭・TREE文脈親・フィルター処理を再利用できる。

取得数減少による回復リセットでは保存済み初回境界を再適用せず、従来どおり単一グループかつバーなしにする。追加更新では従来どおり新しい末尾グループを追加してそのindexを `latestArrivalGroupIndex` にし、新着0件更新では `latestArrivalGroupIndex=null` とする。

### 4. 表示コンポーネントは変更しない

`ThreadVisiblePostsUseCase` が `latestArrivalGroupIndex` のグループを `isAfter=true` にし、`firstAfterIndex` を返す既存経路を維持する。`ThreadPostListContent`、`NewArrivalBar`、`MomentumBar` の条件・見た目・文言・セマンティクスには変更を加えない。

## Implementation Contract

実装エージェントは次の契約に従うこと。

1. `data/util/ThreadNewResCalculator.kt` の計算を、履歴なしは0、履歴ありは `(latestResCount - lastReadResNo).coerceAtLeast(0)` に統一する。`ThreadReadState` のフィールドやDBスキーマは削除・変更しない。
2. `ui/thread/viewmodel/ThreadRouteViewModel.kt` の `ThreadRouteContentState` に、初回ロード用の未読開始レス番号を表す nullable フィールドを追加する。名称は用途が分かるものとし、`firstNewResNo` を流用しない。
3. `initializeTabMetadata` で `tab.newResCount > 0` のときだけ `tab.lastReadResNo + 1` をそのフィールドへ保存する。既存グループを持つcontent stateをRoom再通知で再初期化しない。
4. `setNewArrivalInfo` と `updateThreadLastRead` から初回境界フィールドを更新しない。
5. `applyLoadSuccess` から `updatePostGroups` へ初回境界を渡す。
6. `updatePostGroups` の初回ロード分岐でのみ、境界が取得範囲内なら既読・未読グループを作り、未読側indexを `latestArrivalGroupIndex` に設定する。境界1は単一の未読グループとして扱う。
7. 取得数減少、追加更新、新着0件更新の既存分岐は、初回境界の影響を受けないよう分離する。
8. `ThreadVisiblePostsUseCase.kt`、`ThreadDisplayTransformers.kt`、`ThreadPostListContent.kt`、`NewArrivalBar.kt` の既存表示契約は原則変更しない。テストで既存契約を維持できない事実が判明した場合は実装を止め、OpenSpec設計の再確認を求める。
9. 新規または変更する型・非自明関数にはリポジトリのKDoc規則を適用し、長い関数は既存ルールに従ってセクション分割する。

## Error Cases and Compatibility

- 履歴なし: `newResCount=0` を入口条件として境界を作らず、未訪問スレッドにバーを表示しない。
- 全件既読: `latestResCount <= lastReadResNo` なら新着件数0、境界なしとする。
- 取得数不足・削除レス: 初回境界が実取得レス数を超える場合は分割せず、バーを表示しない。例外や空グループを生成しない。
- `lastReadResNo=0` かつ履歴由来の新着件数が正の場合: 1番から未読グループとして扱う。
- Room再通知: 永続既読位置の進行は板一覧・タブ一覧の件数へ反映するが、表示中content stateの初回境界を上書きしない。
- 互換性: DB migration、データ消去、保存値変換は不要。既存 `firstNewResNo` は他経路とのバイナリ・永続化互換性のため残す。
- ロールバック: アプリコード変更を戻せば従来の単一初期グループへ戻る。永続データ形式を変えないためデータロールバックは不要。

## Testing Strategy

- `ThreadNewResCalculatorTest` で履歴なし、`firstNewResNo` 有無、全件既読、最新数が既読位置未満の計算を `lastReadResNo` 基準で検証する。
- `BoardThreadListTransformUseCaseTest` と必要に応じて `TabsRepositoryThreadStateTest` で、板一覧とタブ一覧が同じ新着件数を導出することを検証する。
- `ThreadRouteViewModelTest` で `lastRead=100`、取得110の初回ロードが `1..100` と `101..110` に分割され、後者が最新グループ、`firstAfterIndex` が101番の表示位置になることを検証する。
- 同テストで `firstNewResNo=null`、履歴なし、全件既読、境界が取得数超過、境界1をそれぞれ検証する。
- 同テストで初回表示後に `updateThreadLastRead` またはタブFlowの既読状態を進めても、`postGroups` と `firstAfterIndex` が変わらないことを検証する。
- 同テストで後続追加更新は新しい末尾グループへバーが移り、新着0件更新ではバーが消える既存契約を検証する。
- `ThreadVisiblePostsUseCaseTest` と `ThreadDisplayTransformersTest` で NUMBER/TREE、検索、NGフィルター、TREE文脈親を含めても未読グループ先頭の `firstAfterIndex` と安定キーが保たれることを検証する。
- 実装後は `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` を実行する。Room結合経路を変更した場合だけ関連instrumented testも実行する。

## Migration Plan

1. 新着件数計算の単体テストを先に更新し、`lastReadResNo` 基準を固定する。
2. `ThreadRouteContentState` へ初回境界スナップショットを追加する。
3. 初回グループ分割を実装し、ViewModelと表示変換の回帰テストを追加する。
4. ビルドと単体テストで既存機能を検証する。

永続化migrationや段階的ロールアウトは不要である。

## Open Questions

なし。初回境界を `lastReadResNo` に統一すること、直接 `firstNewResNo` を表示処理へ渡さないこと、UIを変更しないことは承認済みである。
