## Context

Issue #483 は、タブ一覧でタブを長押ししたときに詳細確認・固定切替・クローズを行えるメニューを追加し、重要なタブを固定できるようにする変更である。既存のタブ一覧は `TabScreenContent` から `TabsPagerContent`、`OpenBoardsList` / `OpenThreadsList`、`RemovableTabList`、`TabListCard` の順で描画され、状態は `TabsViewModel` と板/スレッド別 Coordinator が管理している。

既存の `AnchoredSelectionMenu` は単一選択用途のコンポーネントであり、今回必要な「詳細」「固定切替」「閉じる」のようなタブ操作メニューとは意味が異なる。したがって、既存メニューを拡張するのではなく、`AnchoredOverlayMenu` を再利用したタブ専用の `AnchoredTabActionMenu` を新設する。

固定状態はアプリ再起動後も維持する必要があるため、UI の一時状態ではなく `open_board_tabs` / `open_thread_tabs` に永続化する。既存のタブ順は `sortOrder` で管理されており、固定状態を追加しても表示順は変えず、これまで通り `sortOrder` に従う。

## Goals / Non-Goals

**Goals:**

- 板タブ・スレッドタブの両方で、長押しからタブ専用アクションメニューを表示する。
- タブ専用メニューで詳細表示、固定切替、タブクローズを実行できるようにする。
- 選択中の視覚状態として、選択タブを拡大し、選択タブ以外の表示領域を下部操作群も含めて暗くする。
- 選択中も下部操作群の構造は変えず、既存のボタンとインジケータを画面上に維持する。
- 選択タブ以外の領域をタップしたとき、長押し選択状態とアクションメニューを解除する。
- 固定状態を Room に永続化し、タブ一覧の表示順は固定状態に関係なく既存の `sortOrder` を維持する。
- 固定済みタブの右上表示を閉じるアイコンから表示専用の固定アイコンへ変更する。

**Non-Goals:**

- タブのドラッグ並べ替え機能は追加しない。
- 固定タブのクローズ確認ダイアログは追加しない。
- `AnchoredSelectionMenu` の既存仕様をタブ操作向けに変更しない。
- ジェスチャー設定に固定/解除アクションを追加しない。

## Decisions

### 1. タブ専用の `AnchoredTabActionMenu` を新設する

`AnchoredSelectionMenu` は選択済み値、チェックアイコン、単一選択の見た目を前提としている。今回のメニューは選択ではなくコマンド実行であり、破壊的操作の赤字表示も必要になるため、別コンポーネントとして `AnchoredTabActionMenu` を作る。

代替案として `AnchoredSelectionMenu` に `isDestructive` や任意 text color を追加する方法もあるが、既存の設定系メニューの責務が広がり、選択メニューとアクションメニューの意味が混ざるため採用しない。

タブ専用メニューの各項目には、項目の意味を示す先頭アイコンを表示する。「詳細」は情報アイコン、「タブを固定」は固定アイコン、「タブの固定を解除」は固定解除または固定状態を示すアイコン、「タブを閉じる」は閉じる/削除系アイコンを使う。アイコンとテキストは同じ行に配置し、「タブを閉じる」ではテキストだけでなくアイコンも破壊的操作の色に揃える。

### 2. 長押し選択状態は `TabsUiState` / `TabsViewModel` で管理する

このリポジトリでは画面 UI 状態を `UiState` と `ViewModel` に持たせる方針がある。選択中タブ、アンカー位置、詳細 BottomSheet 表示対象、選択解除はタブ一覧画面全体で参照するため、基本的には `TabsUiState` に集約する。

板タブとスレッドタブは識別子が異なるため、選択対象は型安全な画面状態として分けて保持する。アンカー位置は `TabListCard` 側で `onGloballyPositioned` から `IntRect` を取得し、長押しイベントと一緒に `TabsViewModel` へ渡す。

詳細 BottomSheet の表示対象は、長押し選択対象とは別の state として保持する。`openSelectedTabDetail()` は現在の選択タブを詳細表示用 state（例: `detailBoardTab` / `detailThreadTab`）へコピーしてから長押し選択状態を解除し、BottomSheet は `selectedBoardTab` / `selectedThreadTab` ではなく詳細表示用 state を参照する。これにより、詳細ボタン押下時に `cancelTabSelection()` で選択対象が `null` になっても、BottomSheet の表示フラグと表示内容が消えない。

### 3. 固定状態は Room に永続化する

固定状態は一時的な表示ではなく、ユーザーが継続的に管理したいタブ属性である。そのため、`OpenBoardTabEntity` と `OpenThreadTabEntity` に `isPinned: Boolean = false` を追加し、Room migration で既存行に `false` を付与する。

DAO の一覧取得は既存通り `sortOrder ASC` で並べる。保存時も現在のリスト順を `sortOrder` として保存し、固定状態の切替ではタブの表示順を変更しない。

### 4. 固定済みタブの右上アイコンは固定状態の表示に使う

Issue の受け入れ条件では「閉じるアイコンが固定アイコンに変わる」とされている。固定済みタブではカード右上を固定アイコンとして表示し、固定アイコンは表示専用でタップしても操作を実行しない。固定済みタブを閉じる場合は長押しメニューの「タブを閉じる」から実行する。

この方針により、固定タブの誤クローズや誤解除を避け、固定状態であることを常時認識できる。通常タブは既存通り右上の閉じるアイコンからクローズできる。

カード右上の固定アイコンと閉じるアイコンは、表示領域とアイコン本体サイズを統一する。未固定/固定の切替でカード右上の占有幅や見た目の重心が変わらないようにし、同じ `IconButton` サイズ、同じ `Icon` サイズ、同じ padding 方針を使う。

### 5. 詳細表示は既存 BottomSheet を再利用する

板タブの「詳細」は `BoardInfoBottomSheet`、スレッドタブの「詳細」は `ThreadInfoBottomSheet` を使う。タブ一覧から必要な識別子と表示情報を渡し、既存画面と同じ詳細 UI を提供する。

不足する引数やデータ取得がある場合は、タブ一覧側の ViewModel から既存 Repository / ViewModel 機能へ委譲する。新しい詳細 UI は作らない。

### 6. 下部操作群は長押し選択中も変更しない

長押し選択中でも `TabListBottomControls` の表示は変更しない。タブ作成ボタン、更新/キャンセルボタン、ページ切替表示、スレッド更新中の進捗インジケータは既存と同じ表示を維持する。

これにより、長押し選択中の一時状態によって既存の下部操作群が消えず、ユーザーは通常時と同じ画面構造のままメニュー操作を行える。長押し選択中の下部操作群は dim overlay の下に表示され、タップ時は下部操作ではなく選択解除として扱う。

### 7. 長押し選択中の減光は全画面 overlay と選択タブの再描画で表現する

長押し選択中は、`TabScreenContent` の外側 `Box` で `hazeSource` のコンテンツ層と `TabListBottomControls` を通常通り描画した後に、全画面の dim overlay を重ねる。この overlay はタブ一覧だけでなく下部操作群も覆うため、長押ししたタブ以外の画面要素をまとめて暗く表示できる。

長押ししたタブは overlay より上のレイヤーに `SelectedTabFloatingCard` として再描画する。`TabListCard` に `zIndex` を付けるだけでは、`hazeSource` Box の外にある overlay より前面へ出せないため、選択タブを overlay 上に再描画する方式を採用する。再描画位置は長押し時に取得した `IntRect` を使い、元カード位置と一致させる。

リスト内の元カードは長押し選択中に `alpha(0f)` で透明化し、レイアウト位置だけ保持する。これにより元カードと floating card が二重に見える問題を防ぐ。元カード側の拡大アニメーションは廃止し、選択中の視覚状態は floating card 側だけで表現する。

floating card 側は `Modifier.padding(horizontal = 12.dp)` を持たず、元カードと同じ幅にする。`boundsInWindow()` は window 基準の座標を返すため、floating card の親 `Box` の window 座標を `onGloballyPositioned` で取得し、差分を引いて親基準のローカル座標に変換する。これにより座標系のズレを防ぐ。

floating card の幅は `bounds.right - bounds.left` を `Dp` に変換して明示的に指定する。これにより `fillMaxWidth()` で画面幅いっぱいになって右側がはみ出す問題を防ぐ。

floating card の拡大は `Modifier.scale()` ではなく `graphicsLayer { scaleX/scaleY + TransformOrigin.Center }` で行う。`TransformOrigin.Center` はその Composable の layout 位置を保ったまま描画だけを中心基準に拡大するため、元カード位置に floating card を置くと自然に上下左右へ均等に広がる。

長押し選択解除時の戻りアニメーションは、ViewModel の長押し選択 state を解除しつつ、`TabScreenContent` 側で直前の選択タブと bounds を一時的に保持する方式を採用する。具体的には、`lastSelectedBoardTab` / `lastSelectedThreadTab`、`lastSelectedBounds`、`isExitAnimating` のような Composable ローカル状態を使い、`uiState.selectedBoardTab` / `selectedThreadTab` が `null` になった後も退場アニメーションが終わるまで floating card を描画し続ける。これはアニメーション表示寿命だけの状態であり、タブ操作の真実は引き続き ViewModel に置く。

退場中は floating card を `1.04f` から `1.00f` へ縮小し、dim overlay も通常の解除アニメーションで薄くする。元カードが同時に見えて二重表示にならないよう、`TabScreenContent` は現在選択中または退場中のタブ ID を一覧へ渡し、退場アニメーション完了まで該当元カードを `alpha(0f)` のまま維持する。アニメーション完了後にローカル保持したタブと bounds を破棄し、元カードを通常表示へ戻す。

overlay は `hazeSource` の子に入れず、`hazeSource` と `hazeEffect` の兄弟関係を維持する。これにより、下部操作群の haze 効果を壊さずに、長押し時だけ全画面の減光レイヤーを追加できる。

推奨描画順は次の通りとする。

```text
Box(fillMaxSize)
├── Box(hazeSource)
│   └── TabsPagerContent
├── TabListBottomControls(hazeEffect)
├── LongPressDimOverlay
├── SelectedTabFloatingCard
└── AnchoredTabActionMenu
```

### 8. 選択タブ以外のタップで長押し選択を解除する

`LongPressDimOverlay` は全画面を覆い、タップされたら `TabsViewModel.cancelTabSelection()` を呼び出して長押し選択状態とメニュー表示を解除する。overlay より上に再描画した `SelectedTabFloatingCard` は自身のタップを受け、選択解除を実行しない。

`AnchoredTabActionMenu` の `onDismissRequest`、戻るキー、ページ切替、選択中タブの削除完了、選択中タブが一覧から消えた場合も同じ解除処理に集約する。これにより、メニュー外タップと画面上の非選択領域タップで一貫して選択状態を閉じる。

### 9. ヘッダー右側表示は `TabListCard` 内で値から組み立てる

スレッドタブの通常カードと長押し中の floating card で、レス数や新着レス数の表示がずれないように、ヘッダー右側の描画は呼び出し側の任意 Composable slot ではなく `TabListCard` 内に集約する。呼び出し側は表示内容を表す値だけを渡し、`TabListCard` がレス数テキスト、`+N` 新着バッジ、余白、色、shape を一箇所で描画する。

値の渡し方は、板タブとスレッドタブの差を明示できる型付き表示モデルを使う。例えば `TabHeaderTrailingContent.None` と `TabHeaderTrailingContent.ThreadResCount(resCount, newResCount)` のような sealed interface / sealed class を定義し、板タブは `None`、スレッドタブは `ThreadResCount` を渡す。これにより、単純な `resCount` / `newResCount` 引数を共通カードへ直接追加してスレッド固有概念を増やすより、表示パターンの意味を保ったまま拡張できる。

長押し中の `ThreadTabFloatingCard` も通常の `OpenThreadCard` と同じ `ThreadResCount` を渡す。新着レス数は通常カードと同じ優先順位で、ライブ更新中の `uiState.newResCounts[tab.id.value]` を優先し、存在しない場合は `ThreadTabInfo.newResCount` を使う。これにより、元カードを `alpha(0f)` で透明化して floating card を再描画しても、レス数と新着バッジの見た目が維持される。

### 10. タブアクションメニューは左端揃えと上下自動配置でタブとの重なりを避ける

`AnchoredTabActionMenu` は、長押ししたタブの左端とメニューの左端が概ね揃う位置に表示する。これはメニューをタブの物理的な左外側へ完全に出す意味ではなく、既存の `HorizontalAnchorAlignment.Start` 相当の「anchor 左端基準」の配置を使う。画面端で完全一致できない場合は、画面内に収まるように clamp し、完全一致よりも可視性を優先する。

縦方向は固定で上側に出すのではなく、`VerticalAnchorAlignment.Auto` 相当の位置決定を追加する。長押しタブより上の空きと下の空きを比較し、上側の空きが大きい場合はメニューをタブの上側へ、下側の空きが大きい場合はタブの下側へ表示する。タブとメニューが重ならないように、現在の overlap ではなく gap を使い、上側表示では `menu.bottom <= tab.top - gap`、下側表示では `menu.top >= tab.bottom + gap` になるように配置する。

この挙動はタブ専用メニューで必要なため、`AnchoredOverlayMenu` には既存利用を壊さないデフォルト値を残したまま、縦方向 alignment と非重なり gap を指定できる API を追加する。既存の画像ビューアや設定系メニューは既定値のまま維持し、`AnchoredTabActionMenu` だけが `HorizontalAnchorAlignment.Start` と `VerticalAnchorAlignment.Auto` を指定する。

## Risks / Trade-offs

- 固定済みタブの右上から直接閉じられなくなる → 長押しメニューに「タブを閉じる」を常に表示し、閉じる導線を維持する。
- `ThreadInfoBottomSheet` に必要な情報が `ThreadTabInfo` だけで不足する可能性がある → 実装時に既存の板画面/スレッド画面での呼び出しを確認し、必要に応じて ViewModel 経由で既存データを取得する。
- Room migration の追加により既存 DB 互換性が必要になる → `ALTER TABLE ... ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0` を板/スレッド両テーブルに適用し、migration test を追加する。
- 固定状態を変更してもタブが上位へ移動しないため、固定の意味が視覚表示中心になる → 固定済みタブは右上の固定アイコンと長押しメニューの解除ラベルで明確に示す。
- 長押し選択中のアニメーションが既存の削除アニメーションと干渉する可能性がある → `isRemoving` 中のカードでは長押し・クリック・メニュー操作を無効化する。
- dim overlay が下部操作群のタップを遮る → 長押し選択中は下部操作群自体を操作対象にせず、下部操作群上のタップも選択解除として扱う。
- 選択タブ再描画と元カードが二重に見える可能性がある → 元カードは `alpha(0f)` で透明化しレイアウト位置だけ保持する。選択中の視覚状態は floating card 側だけで表現する。
- overlay を `hazeSource` の子に入れると下部操作群の haze 効果に影響する可能性がある → overlay は外側 `Box` の sibling として追加し、`hazeSource` / `hazeEffect` の兄弟関係を維持する。
- floating card と元カードの位置がズレる可能性がある → floating card 側に `padding(horizontal = 12.dp)` を入れず、`IntRect` を `IntOffset` でそのまま配置する。元カード側の scale アニメーションは廃止し、bounds 取得時と floating card 表示時のサイズを一致させる。
- `TabListCard` にスレッド固有の表示概念が入りすぎる可能性がある → `resCount` / `newResCount` を個別引数にせず、ヘッダー右側の表示パターンを表す型に閉じ込め、板タブでは `None` を渡す。
- メニューを左端揃えにすると画面左端付近で anchor 左端と完全一致できない可能性がある → 画面内に収める clamp を優先し、完全一致は必須条件にしない。
- メニューを上下自動配置しても上下どちらにも十分な余白がない可能性がある → 空きが大きい側を選んだうえで画面内に clamp し、可能な範囲でタブとの gap を維持する。
- 詳細ボタン押下時に長押し選択状態を解除すると、選択タブを参照している BottomSheet が表示できなくなる可能性がある → 詳細表示用 state を選択 state から分離し、詳細表示対象をコピーしてから選択状態を解除する。
- 戻りアニメーション中に元カードと floating card が二重に見える可能性がある → `TabScreenContent` のローカル退場状態で保持しているタブ ID も一覧へ渡し、退場完了まで元カードを透明化する。

## Migration Plan

1. Room database version を 1 つ上げる。
2. `open_board_tabs` と `open_thread_tabs` に `isPinned INTEGER NOT NULL DEFAULT 0` を追加する migration を定義する。
3. `DatabaseModule` など migration 登録箇所へ追加する。
4. 既存ユーザーのタブはすべて未固定状態として移行する。
5. ロールバック時はアプリの旧バージョンが新 DB schema を読めないため、通常のアプリ更新ロールバック制約に従う。

## Open Questions

- 退場アニメーション中にページ切替やタブ一覧更新が発生した場合、保持中の floating card を即破棄するか、現在の短い退場アニメーションを完了させるか。
