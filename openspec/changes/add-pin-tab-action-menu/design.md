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

### 2. 長押し選択状態は `TabsUiState` / `TabsViewModel` で管理する

このリポジトリでは画面 UI 状態を `UiState` と `ViewModel` に持たせる方針がある。選択中タブ、アンカー位置、詳細 BottomSheet 表示対象、選択解除はタブ一覧画面全体で参照するため、Composable ローカル状態ではなく `TabsUiState` に集約する。

板タブとスレッドタブは識別子が異なるため、選択対象は型安全な画面状態として分けて保持する。アンカー位置は `TabListCard` 側で `onGloballyPositioned` から `IntRect` を取得し、長押しイベントと一緒に `TabsViewModel` へ渡す。

### 3. 固定状態は Room に永続化する

固定状態は一時的な表示ではなく、ユーザーが継続的に管理したいタブ属性である。そのため、`OpenBoardTabEntity` と `OpenThreadTabEntity` に `isPinned: Boolean = false` を追加し、Room migration で既存行に `false` を付与する。

DAO の一覧取得は既存通り `sortOrder ASC` で並べる。保存時も現在のリスト順を `sortOrder` として保存し、固定状態の切替ではタブの表示順を変更しない。

### 4. 固定済みタブの右上アイコンは固定状態の表示に使う

Issue の受け入れ条件では「閉じるアイコンが固定アイコンに変わる」とされている。固定済みタブではカード右上を固定アイコンとして表示し、固定アイコンは表示専用でタップしても操作を実行しない。固定済みタブを閉じる場合は長押しメニューの「タブを閉じる」から実行する。

この方針により、固定タブの誤クローズや誤解除を避け、固定状態であることを常時認識できる。通常タブは既存通り右上の閉じるアイコンからクローズできる。

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

floating card 側は `Modifier.padding(horizontal = 12.dp)` を持たず、元カードの `boundsInWindow()` と一致させる。`IntRect` のまま `IntOffset` で配置し、dp 変換による端数ズレを防ぐ。floating card には scale（1.00 → 1.04）と elevation のアニメーションを付け、元カードから連続して拡大するように見せる。

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

## Migration Plan

1. Room database version を 1 つ上げる。
2. `open_board_tabs` と `open_thread_tabs` に `isPinned INTEGER NOT NULL DEFAULT 0` を追加する migration を定義する。
3. `DatabaseModule` など migration 登録箇所へ追加する。
4. 既存ユーザーのタブはすべて未固定状態として移行する。
5. ロールバック時はアプリの旧バージョンが新 DB schema を読めないため、通常のアプリ更新ロールバック制約に従う。

## Open Questions

- 詳細 BottomSheet の表示に必要な情報がタブ情報のみで足りるか。実装時に既存 BottomSheet の引数を確認して最小追加にする。
