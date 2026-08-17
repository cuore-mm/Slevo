## Context

`RemovableTabList.kt` は現在、各Lazy itemへ同じ200msの `animateItem` appearance/disappearance/placementを設定する。削除後に、削除前から可視だった残存カードと画面上端外から新たに可視化された残存カードでplacementの開始位置が異なる場合、別カード同士が一時的に同じ領域へ描画される。

既存の単体削除は `RemovableTabList` のローカル `removingItems` を設定した直後にStoreへ削除を渡し、projectionから行が消えたことでLazy itemの退出を開始する。一括削除は `TabListViewModel.closeAllUnpinnedTabs` からStoreのbulk APIを即時に呼び、全対象を1つのprojection operationで除外する。一括削除の性能・選択・永続化契約は `optimize-bulk-tab-close` で完成済みであり、本changeはその入口より前のUI表現だけを変更する。

## Goals / Non-Goals

**Goals:**

- 閉じるボタン、長押しメニュー、一括クローズで、削除対象行の高さと透明度を同時に0へ変化させる。
- 周囲のカードを独立したplacement animationではなく、削除行の縮小に追従する通常レイアウトとして移動させる。
- 削除中keyを `TabListUiState` が所有し、対象カードへの二重操作を防ぐ。
- 既存の200ms定数を退出時間と削除開始遅延に共用する。
- アニメーション後も既存の単体closeまたはbulk close APIを各操作につき1回だけ呼ぶ。

**Non-Goals:**

- Undo、確認、進捗表示、失敗時の逆再生を追加しない。
- アニメーション待機中の画面破棄、外部削除、pin変更など低頻度競合へ新しい保証を追加しない。
- スワイプ削除の140ms横方向飛び出し、判定値、入力処理を変更しない。
- Store、Coordinator、projection、選択補正、holder破棄、Repository、DAO、Room schemaを変更しない。
- `add-bulk-delete-tabs` のその他ボタン、メニュー項目、文言、表示中 `TabPage` 境界を変更しない。

## Decisions

### 1. 削除前のUIフェーズを `TabListUiState` で表現する

`TabListUiState.kt` にBoard URL集合とThread key集合の削除中状態を追加する。`TabListViewModel.kt` は閉じるボタン・長押し・bulkイベントを受けた時点で対象keyを登録する。bulkイベントはクリック時の対象snapshotと `TabListAnimationDefaults.ITEM_REMOVAL_MILLIS` をActivity-retained `TabSessionStore`へ渡し、待機と既存Store API呼び出しをretained scopeで行う。

単体イベントは対象モデルをdelay開始時に保持する。一括イベントはクリック時の公開projectionから表示中ページの未固定keyを一度取得してUiStateへ登録し、同じ対象snapshotをretained Store APIへ1回渡す。Coordinatorがbulk commandを受理した後の1 pending、1 projection、選択、transaction、GC、holder破棄契約は変更しない。

削除中keyは、公開projectionからkeyが消えた時点でViewModelイベントによりUiStateから除去する。削除失敗の逆アニメーションや待機中の画面破棄は本changeでは扱わない。

代替としてComposableローカルstateを維持する案は、bulkメニューから複数keyを同時に開始しにくく、リポジトリ規約の「画面UI stateはViewModel所有」に反するため採用しない。Storeのretained scopeで待機する案を採用し、UI定数は遅延APIの引数として渡す。

### 2. `AnimatedVisibility` がカード本体と行間余白を縮小する

`RemovableTabList.kt` は呼び出し元から `removingKeys: Set<String>` を受け、各Lazy item内で `AnimatedVisibility(visible = itemKey !in removingKeys)` を使用する。退出は100msの線形 `fadeOut` と、40ms後に開始する160msの `shrinkVertically` を組み合わせる。高さ縮小の終了時点は既存の削除開始待機200msと一致させる。

現在の `Arrangement.spacedBy(verticalSpacing)` を残すと、カードの高さが0になってもLazy item間の固定余白が残り、実データ削除時に余白分だけ最後に跳ねる。そこでLazyColumnの固定spacingを外し、カードとその下の `verticalSpacing` を同じ縮小タイミングの `AnimatedVisibility` で制御する。これによりカード高と行間余白が同じ終了時点で0になる。通常表示時のカード間隔は変更しない。

### 3. 削除時のLazy item placement/disappearanceを無効化する

`Modifier.animateItem` の `placementSpec` と `fadeOutSpec` をnullにする。削除行の退出は `AnimatedVisibility` だけが担当し、周囲のカードは毎フレーム変わる行高に通常レイアウトで追従する。新規タブの `fadeInSpec` は維持する。

`zIndex`、clip、placement時間短縮はカード同士の位置交差をなくさないため採用しない。全placementを維持したまま画面外itemだけ特別扱いする案も、Compose内部の可視範囲判定へ依存するため採用しない。

### 4. スワイプ削除は既存専用アニメーションを維持する

スワイプ確定時は `TabListCard.kt` が140msでカードを左へ画面外へ移動してから削除callbackを呼ぶ既存経路を維持し、追加の縮小・fadeを開始しない。`RemovableTabList` と `OpenBoardsList.kt` / `OpenThreadsList.kt` は、通常削除要求とスワイプ確定後の削除要求を区別して呼び出す。

これにより、画面内に残ったカードを詰める操作では縮小方式を使い、既に画面外へ退出したスワイプカードへ二重の退出アニメーションを適用しない。

### 5. 既存bulk処理はアニメーション後に1回だけ開始する

`TabListViewModel.closeAllUnpinnedTabs(page)` は、先にメニューを閉じ、対象0件ならUiStateとStoreを変更せず終了する。対象がある場合は全keyを同時に削除中へ設定し、対象snapshotと200ms待機をretained Store APIへ1回渡す。行ごとの単体closeへ分解してはならない。

対象snapshotをStoreへ渡した時点でbulk要求を受理し、Store retained scopeが200ms後に同じ対象をCoordinatorへ渡す。そこから全対象が1 projection operationで除外されるため、アニメーション開始後のpin変更や新規タブ追加で削除対象が変わらない。

## Implementation Contract

1. `TabListUiState` はBoardとThreadを区別した削除中key集合を保持する。
2. `TabListViewModel` は通常削除とbulk削除でkeyを登録する。bulk削除はクリック時の対象snapshotと既存200ms定数をretained Store APIへ渡し、待機後に既存bulk Coordinator APIを各操作1回呼ぶ。
3. `RemovableTabList` のローカル `removingItems` と `externalRemoveKey` による即時Store呼び出しを、UiState駆動の `removingKeys` 表示へ置き換える。
4. `AnimatedVisibility` はカードを100msで線形fade-outし、40ms後から160msでカードと行間余白を上端方向へ縮小して、通常時の12dp相当の間隔を維持する。
5. `animateItem` は追加時fade-inだけを維持し、削除時fade-outとplacementを適用しない。
6. 閉じるボタン、長押しメニュー、bulkは縮小経路へ統合し、スワイプ確定後は既存専用退出からStoreへ直接渡して縮小を重複させない。
7. 削除中カードのタップ、長押し、閉じる、スワイプの再実行を禁止する。
8. bulkは対象snapshotをretained Storeへ1回だけ渡し、アニメーション後も対象件数分の単体closeへ展開しない。
9. その他ボタン、メニュー文言、`TabPage`、Coordinator/Repository/DAO API、Room schema、選択計算を変更しない。
10. 新規・変更型と非自明関数へアノテーションより上にKDocを置き、Preview関数へKDocを追加せず、30行超関数をセクションコメントで分割する。

## Error Cases and Compatibility

- 対象0件のbulk: メニューだけを閉じ、削除中key、Store、Coordinatorを変更しない。
- 同じkeyの再削除: 削除中集合を確認して新しいdelayやStore呼び出しを作らない。
- スワイプ削除: 横方向退出だけを表示し、縮小・fadeを重複表示しない。
- Board/Threadページ: key集合を分離し、同じ文字列表現でも反対ページへ削除中表示を漏らさない。
- 検索結果: 同じkey集合を通常・検索リストへ適用し、表示中の該当カードを縮小する。
- アニメーション後: 既存Store API以降のNoOp、Failure、canonical確認、retained処理は対象snapshotで維持する。

## Testing Strategy

- `TabListViewModelTest.kt`: 単体Board/Threadでkey登録、bulk対象snapshotのStore渡し、同一key二重要求NoOp、bulk対象key同時登録、対象0件NoOp、bulk Store呼び出し1回を検証する。`TabSessionStoreTest.kt`ではretained遅延、caller/ViewModel破棄相当、待機中のprojection変化を検証する。
- Compose test: 閉じる操作後に対象カード高とalphaが遷移し、開始時・中間時・完了時に残存カード同士のboundsが交差しないこと、全対象bulkが同時に縮小すること、固定カードが縮小しないことを検証する。
- Swipe regression: スワイプ確定時は左方向退出後に削除され、縮小exitを重複表示しないことを維持する。
- Existing regression: `TabSessionStoreTest.kt`、Board/Thread Coordinator test、bulk projection/selection/chunk/GC testを変更せず維持する。
- CI相当は `./gradlew testCiUnitTest assembleCi --stacktrace`。Compose instrumented testを実行できるAndroid環境がなければ未実行理由を報告する。

## Risks / Trade-offs

- [削除確定が200ms遅れる] → 視覚退出時間として意図的に許容し、定数をアニメーションとdelayで共用する。
- [ViewModel破棄でdelayがcancelされる] → delayと対象snapshotをStore retained scopeへ移し、受理済みbulkを完了する。
- [アニメーション中に対象集合が変わる] → クリック時snapshotをStoreからCoordinatorまで保持し、新規タブやpin変更で対象を置き換えない。
- [AnimatedVisibilityと固定spacingの組合せで最後に跳ねる] → spacingを退出content内へ移して高さと同時に縮める。

## Migration Plan

DB・データ移行は不要。UiState、ViewModelイベント、リスト表示を同一リリースで変更する。問題時はUiState削除中フェーズと `AnimatedVisibility` を外し、既存 `animateItem` placement/disappearanceへ戻せる。

## Open Questions

なし。固定200ms、低頻度edge caseを扱わないこと、縮小とfadeを使用することは承認済みである。
