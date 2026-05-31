## Context

タブ一覧画面は `TabScreenContent` が `PagerState` を保持し、`TabsPagerContent` の `HorizontalPager` で板タブ一覧とスレッドタブ一覧を表示している。下部の `TabListBottomControls` には既に板/スレを明示的に選択する switch があり、ページ切替の入口は下部操作群として成立している。

各タブカードは `OpenBoardsList` / `OpenThreadsList` から `RemovableTabList` 経由で `TabListCard` として描画される。削除は閉じるボタンまたは長押しアクションメニューから既存の `closeBoardTab` / `closeThreadTab` へ流れ、`RemovableTabList` が削除中状態と `animateItem` による退場アニメーションを管理している。固定済みタブは `isPinned` により閉じるボタンが表示されず、固定アイコンのみが表示される。

## Goals / Non-Goals

**Goals:**

- 板/スレ一覧のページ切替を下部 switch のタップ操作へ集約し、横スワイプではページを切り替えない。
- 板タブ・スレッドタブの未固定カードを右から左方向への横スワイプで削除できる。左から右のスワイプは削除対象外とする。
- カードのスワイプ移動量上限をカード幅とし、削除判定はカード幅に基づく移動距離と `VelocityTracker` で算出したスワイプ速度の両方を考慮する。
- スワイプ削除確定時に、カードを左方向へ飛び出させる専用退出アニメーションを追加する。ボタンや長押しメニューによる削除は従来の `animateItem` 退出アニメーションを維持する。
- 固定済みタブ、長押し選択中、削除アニメーション中はスワイプ削除を実行しない。

**Non-Goals:**

- タブ固定・固定解除の仕様変更。
- タブ削除の永続化方式や repository API の変更。
- タブ一覧以外の板画面・スレッド画面にあるタブスワイプ挙動の変更。
- スワイプ削除後の Undo 機能追加。

## Decisions

### Decision 1: `HorizontalPager` は残し、ユーザースクロールだけを無効化する

`TabsPagerContent` の `HorizontalPager` に `userScrollEnabled = false` を指定し、下部 switch からの `animateScrollToPage` によるページ切替は維持する。`HorizontalPager` を `when` 分岐へ置き換える案もあるが、既存の `PagerState`、下部 switch の選択状態、ページ変更通知を最小変更で維持できるため、ユーザースクロール無効化を採用する。

### Decision 2: スワイプ削除の入口は `TabListCard` に集約する

横スワイプ gesture と視覚的なオフセット表示はカード単位の責務として `TabListCard` に追加する。`OpenBoardsList` / `OpenThreadsList` は、カードがスワイプ削除を確定したときに既存の `requestRemove()` を呼び出すだけにする。

この構成により、板タブとスレッドタブで同じスワイプ挙動を共有でき、削除確定後の状態更新・退場アニメーションは既存の `RemovableTabList` と ViewModel 経路に統一できる。

### Decision 3: スワイプ可否は UI 状態から明示的に渡す

`TabListCard` には `isSwipeDeleteEnabled` のような真偽値を渡し、カード内部では `isSwipeDeleteEnabled && !isPinned` の場合のみ横スワイプを有効にする。呼び出し側では `TabsUiState.isInLongPressSelectionMode` と `isRemoving` を使って通常時以外のスワイプを抑止する。

ViewModel に「スワイプ削除専用」の新状態を増やす案もあるが、今回の可否判定は既存 UI 状態とカード状態から決定できるため、状態追加は避ける。

### Decision 4: 削除確定後は既存の削除フローへ合流する

スワイプがしきい値を超えた場合、カードは `onSwipeDelete` を通知し、一覧側は `requestRemove()` を実行する。これにより閉じるボタンと同じ二重操作防止、ViewModel の `closeBoardTab` / `closeThreadTab` を利用する。

### Decision 5: スワイプ移動量上限をカード幅とする

カードの横スワイプ移動量上限は `onGloballyPositioned` で測定したカード幅を上限とする。固定値（例: 120dp）ではなくカード幅に比例することで、画面サイズやレイアウト変更に応じた自然な操作性を確保する。削除しきい値はカード幅の 55% とし、一定距離以上のスワイプで意図的な削除操作とみなす。

### Decision 6: スワイプ削除は右から左方向のみ有効とする

スワイプによる削除は右から左方向（カードを左へドラッグ）のみ有効とし、左から右方向のスワイプは削除対象外とする。`offsetX` は `coerceIn(-cardWidthPx, 0f)` で負の値のみを許可し、正方向へのドラッグは無視する。これにより誤削除を抑制し、一般的なスワイプトゥディスmiss（右から左）のUXに合わせる。

### Decision 7: スワイプ削除確定時に専用の退出アニメーションを表示する

スワイプがしきい値を超えた場合、即座に `onSwipeDelete` を呼ばず、`offsetX.animateTo(-cardWidthPx)` による専用退出アニメーションを再生する。アニメーション完了後に `onSwipeDelete` を呼び出し、既存の削除フローへ合流させる。ボタンや長押しメニューによる削除は従来どおり `animateItem` のフェードアウトを利用する。これにより、スワイプ削除ではカードが左方向へ飛び出す専用アニメーションが表示され、従来のアニメーションは重複しない。

### Decision 8: 削除判定は距離と `VelocityTracker` の速度を組み合わせる

スワイプ削除の確定判定は、カード幅に対する移動距離だけでなく、`VelocityTracker` が算出する右から左方向の速度も考慮する。距離判定はカード幅の 55% をしきい値とし、速度判定は 800dp/s 相当を `LocalDensity` で px/s に変換した値をしきい値とする。速度だけで微小な揺れが削除として扱われないよう、速度判定には 24dp 相当以上の左方向移動も必要とする。

現在の `detectHorizontalDragGestures` は `onDragEnd` に速度を渡さないため、速度判定を正確に扱うために `VelocityTracker` を使う低レベルの pointer input へ置き換える。判定は「左方向への移動距離がカード幅の 35% を超える」または「左方向の速度が 700dp/s 相当を超え、かつ左方向移動が 20dp 相当以上」のいずれかで削除確定とする。速度・最小移動距離は dp 基準で定義し、端末密度差による体感差を抑える。

## Risks / Trade-offs

- 横スワイプ gesture とカードの `combinedClickable` / 長押しが競合する可能性がある → 横方向のドラッグしきい値を設け、削除確定前はクリック・長押しの既存操作を損なわないようにする。
- `LazyColumn` の縦スクロールとカード横スワイプが競合する可能性がある → 横方向の移動量が優勢な場合のみカードスワイプを処理し、縦スクロールの操作感を検証する。
- 固定タブで反応がないことが分かりにくい可能性がある → 固定済みタブは既存の固定アイコン表示を維持し、スワイプ削除の対象外であることを視覚的に示す。
- 速度判定により意図しない高速な短距離操作が削除になる可能性がある → 速度しきい値に加えて 20dp 相当の最小移動距離を要求し、固定タブ・長押し選択中・退出アニメーション中の無効化を維持する。
- `HorizontalPager` を残すことでページ構造は維持される → 今回は挙動変更を最小化するため許容し、将来の責務整理で `when` 表示へ置き換える余地を残す。

### Decision 9: 横スワイプと縦スクロールの競合は `DragMode` で方向ロックする

`detectHorizontalDragGestures` は親の `LazyColumn` 縦スクロールとの競合をうまく裁けない。指を置いて少し横に動かした後、上下に動かすと、カードが横に offset されたままリストが縦にスクロールしてしまう。

これを解決するため、`DragMode` enum（`Undecided`、`HorizontalSwipe`、`VerticalScroll`）を導入し、pointer input を低レベルで自前判定する。`Undecided` 状態では touch slop を超えた移動量で方向を判定し、横移動が優勢なら `HorizontalSwipe` を確定してイベントを consume し、縦移動が優勢なら `VerticalScroll` を確定して `offsetX` を 0 に戻し、イベントは consume せず `LazyColumn` に任せる。

この方式により、横スワイプ中は縦スクロールが起きず、縦スクロール中はカードの横 offset が残らない自然な操作感を実現する。速度判定・距離判定・専用退出アニメーションは `HorizontalSwipe` 確定後に既存の `VelocityTracker` ロジックをそのまま適用する。

### Decision 10: gesture 入力面と描画移動面を分離し、差分計算は `positionChange()` 累積を使う

`pointerInput` を `offsetX` で移動する `Card` から、移動しない外側 `Box` へ移動する。これにより、ドラッグ中に入力座標系そのものが移動して `dx` の符号が不安定になる問題を避ける。

方向判定と移動量計算は `change.position` の絶対座標差分ではなく `positionChange()` の累積値を使う。`totalDx` / `totalDy` はイベントごとの差分を積み上げて判定し、`offsetX` は `totalDx.coerceIn(-cardWidthPx, 0f)` で直接算出する。`VelocityTracker` にはアニメーション済み offset ではなく、差分累積で構成した追跡座標を渡し、指の入力速度に近い値を使って削除判定する。

### Decision 11: 削除不可スワイプはラバーバンド追従＋spring復帰で表現する

右方向スワイプ、および固定タブなど削除不可状態でのスワイプは、完全に無反応にせず少量だけ追従させる。追従量は `positionChange()` 累積から算出した移動量にラバーバンド補正を適用し、閾値付近で急停止せず抵抗が強まる挙動にする。

指を離した時は `spring` による 0 位置復帰を適用し、ゴムを離したような自然な戻りを作る。削除可能な左方向スワイプのみ従来どおり距離・速度判定を行い、削除不可スワイプでは削除判定を実行しない。長押し選択中は従来どおりスワイプ自体を無効化する。
