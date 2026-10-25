# Design

## Context

目的は`proposal.md`を参照。本changeは実装済み`add-thread-reply-previews`の後続であり、そのdeltaを仕様基点として変更する。旧changeとアプリコードは計画時に変更しない。

ソースパスは以下、`app/src/main/java/com/websarva/wings/android/slevo/`からの相対パス。

| 現行箇所 | 現行の動作 |
| --- | --- |
| `data/util/ReplyAnchorParser.kt` | `parseAnchors`が範囲を保持、`extractReferencedNumbers`が取得済み上限に制限して展開する |
| `ui/thread/viewmodel/ThreadReplyPreviewTransformer.kt` | `build`の候補生成で未取得・NGを除外、最初の行から一件だけを選ぶ。`buildBodySegments`は複数安価の元行を残す |
| `ui/thread/state/ReplyPreviewUiState.kt` | `replyTarget`と`replyTargetNumbers`は投稿全体に一つ。`ReplyTargetPreviewUiState.post`は実レス必須 |
| `ui/thread/res/PostItemBody.kt` | `TextRange`と`ReplyTarget`を順に描画し、全文の注釈を`subSequence`で切り出す |
| `ui/thread/viewmodel/ThreadRouteViewModel.kt` | `addPopupForReplyNumbers`、`resolveRenderablePostNumbers`、`prunePopupStackWithoutRenderablePosts`がNG・未取得を除外し、空の一覧を破棄する |
| `ui/thread/res/ReplyPopup.kt` | 通常レスだけを行にし、NG・未取得行を描画しない |

新要件では返信先表示だけにNG候補の一時開示を導入し、未取得番号は範囲表示として分離する。通常の安価リンク、ID・ツリー・返信元一覧まで一括で除外解除しない。

## Goals / Non-Goals

**Goals:** 行単位で取得済み候補・選択・残数と未取得範囲を扱う。本文分割による元位置とリンク注釈を維持する。NGの一時開示を同一スレッドのレス番号単位で共有し、非表示の内容を漏らさない。

**Non-Goals:** 返信元・通知のNG方針変更、NGルール自体の解除、未取得番号の自動取得、DB移行、永続的な開示設定、通常安価リンク全般の対象変更、未確定のカードと本文の余白量変更。

## Decisions

### 1. 共通パーサは維持し、候補列挙を用途別に分ける

`ReplyAnchorParser.parseAnchors`のtoken/rangesを利用し、取得済み参照番号と未取得区間を分けて返す返信先専用APIを追加する。APIには取得済みレス数を渡す。取得済み範囲との交差部分だけ番号を出現順に重複除去して列挙し、レス数を超える部分は番号列挙せず区間として返す。

番号は正のInt範囲だけを有効とし、既存の無効構文拒否を流用する。取得済み番号の訪問数は実際の取得済みレス範囲内に限る。未取得区間は重複・隣接を統合し、Long境界比較でInt最大値のオーバーフローを防ぐ。範囲を全番号へ展開する実装は禁止する。処理量は取得済み範囲と入力token数に比例し、未取得範囲の長さには比例しない。

`extractReferencedNumbers`の取得済み上限による解決は返信関係・通知・通常リンク用として維持する。共通APIを一括で無制限化する案は互換性と性能を壊すため採らない。

### 2. 行ごとの候補と状態をViewModel派生データへ保持する

`ReplyPreviewUiState.kt`に次の責務の型を用意する（以下の新しい型名は提案名）。

- `ReplyTargetCandidateUiModel`: `postNumber`、正常/NGの状態。取得済み候補のプレビュー情報は非表示側が描画しない形で保持または番号から解決する。
- `ReplyTargetPreviewUiState`: 安価行の元offset群、行単位の安定キー、順序付き取得済み候補、統合済み未取得範囲、選択番号。取得済み候補数−1を行内の「ほかN件」とする。
- `PostReplyPreviewUiState`: 単数の`replyTarget`と投稿全体の`replyTargetNumbers`を行別集合へ置換する。返信元のフィールドと導出は維持する。

取得済み候補内で正常の先頭、なければNGの先頭を選ぶ。取得済み候補がなければ未取得範囲の状態だけを表示する。開示操作で選択番号を変えたり、候補順を並べ替えたりしない。取得済み候補の重複除去は行内だけで行い、別行に同じ番号があれば両方へ挿入する。範囲内の取得済み番号だけを走査し、未取得部分は区間演算で重複・隣接を統合する。NG・取得結果が変われば分類と選択を再導出する。

`ThreadReplyPreviewTransformer`は対象行をすべて処理し、各行を`ReplyTarget`へ置換する。`isSingleAnchor`による元行再追加は廃止する。同フィールドに他用途がなければ型・Preview・テストから削除する。本文offsetを単調増加カーソルで走査し、先行本文範囲、カード、後続本文範囲を生成する。既存の置換に伴う境界改行処理を行ごとに適用し、通常本文中の空行・非対象安価・文中リンクは残す。連続安価行でoffset範囲を重ねない。

常にカードを投稿先頭へ集める案は本文中の挿入位置を失うため採らない。`PostItemBody`のセグメント描画は維持し、一覧操作は投稿全体でなくそのセグメントの候補集合を渡す。

### 3. 返信先一覧専用の表示方針を追加する

`PopupInfo.kt`へ通常一覧と返信先候補一覧を区別する種別を追加する。返信先専用のイベント入口を`ThreadRouteViewModel`へ設け、`PostItemBody`→`PostItem`→一覧/画面→`ThreadScaffold`から接続する。新入口は取得済み候補番号だけを渡し、候補がない場合は一覧を開かない。既存の`onReplyFromClick`や通常リンク入口を一括置換しない。

`ReplyPopup`は返信先モードに限り、取得済み候補を通常の`PostItem`形式で表示する。NG非表示時は既存投稿ヘッダー内に番号・状態・「表示」だけを描画し、開示時は通常の`PostItem`に末尾の「隠す」操作だけを追加する。全件NGでも取得済み候補がある一覧は保持するが、候補がない場合や更新で全候補が解決不能になった場合は一覧を開かず閉じる。通常モードは現行フィルタを保つ。一覧は選択番号も含む全取得済み候補を表示し、未取得範囲は含めない（「ほか」は選択を除く取得済み候補数）。重複ポップアップ判定には種別・番号順を含め、共有開示状態は含めない。既存の`popupId`、段数・位置・インデント・スクロール・画像namespaceを維持する。

返信先カード本体から選択レスを単体表示する操作も、同じ専用入口へ選択番号だけを渡す。ポップアップは同一スレッド内のレス番号別開示stateを参照するため、プレビューなど別箇所で開示済みのNGレスは開示状態で表示する。未取得範囲は非操作テキストとしてインラインカード内だけに表示する。取得済み候補の表示ボタンと省略操作だけを提供し、取得済み候補がない行では一覧を開く操作を設けない。残数ゼロでは「ほか0件」を出さない。

番号に対応する投稿がない行ではID集計等へ添字アクセスしない。更新で未取得→正常になれば同じ番号の通常レスへ遷移する。自動取得は追加しない。

### 4. 一時開示はスレッド内のレス番号単位で共有する

`ThreadUiState`と`ThreadRouteViewModel`で開示対象のレス番号集合とイベントを管理する。集合はスレッドごとの`ThreadRouteContentState`に所有させ、キーには表示箇所、ホスト投稿番号、安価行index、`popupId`を含めない。Composable内の`remember`を唯一の状態所有元としない。

「表示／隠す」は同じスレッド内の同一レス番号に対する全表示箇所の状態を切り替える。専用カード/番号ヘッダーは追加せず、既存の返信先UIに開閉操作だけを加える。インラインでは`ReplyPreviewCard`の既存サーフェスと一行ヘッダー（番号・名前・ID）を維持し、日時を追加しない。NG開示中はヘッダー右端へ「隠す」を置き、三行本文・画像は通常プレビューと同じ配置で表示する。非表示中は同じ枠に番号・NG状態・「表示」だけを出し、名前・ID・本文・画像をcompositionへ入れない。返信先一覧では既存`PostItem`と`PostItemHeader`をそのまま使い、開示中のみヘッダー右端に「隠す」を追加する。非表示行も既存投稿ヘッダーと同じ番号・状態・操作の並びにし、投稿の名前・ID・本文・画像はcompositionへ入れない。どちらの操作も48dp以上のTextButtonとし、NG設定を書き換えず新規ポップアップを開かない。

ポップアップを閉じても、同じスレッド内で対象レスがNGである間は開示状態を保持し、再表示時にも開示する。NGルールのsnapshot変更時は既存どおり開示集合をクリアし、スレッド破棄時もスレッド所有stateとともに破棄する。対象レスがNGでなくなった場合や取得対象から消えた場合は開示番号を破棄し、新たにNGになったレスは明示操作まで非表示とする。無関係なstate更新で本文解析を再実行しない既存キャッシュ境界を維持する。

同じレス番号の開示はスレッド内だけで共有し、別スレッドに同番号のレスがあっても状態を共有しない。開示によってNG判定、返信元一覧・件数、返信通知、通常リンクの対象選別を変更しない。

### 5. 文言・アクセシビリティ・Preview

`PostItemHeader`には通常の投稿番号・名前・日時・IDの並びを変えずに末尾操作を追加できるようにし、開示中の返信先ポップアップの「隠す」に使用する。非表示状態は同ファイルの投稿ヘッダー書式を使って番号・状態・「表示」だけを描画する。インラインは`ReplyPreviewCard`の既存ヘッダーへ同じ操作を配置する。NG非表示時の本文・名前・ID・画像はcompositionへ入れない。`ReplyTargetStatusRow.kt`を廃止し、未取得範囲は「ほかN件」と共通の`ReplyPreviewFooter.kt`内に配置する。

`strings_reply_preview.xml`へ「NGで非表示」「表示」「隠す」と未取得範囲の表示文言を追加する。未取得範囲は非操作テキストとしてインラインカード内の「ほかN件」直下だけに配置する。「ほかN件」は選択を除く取得済み候補だけを数える。返信先一覧は取得済み候補だけを表示する。

表示/隠すは48dp以上とし、読み上げ名で対象番号と操作を伝える。状態・ボタン・一覧操作・画像操作を独立フォーカスにする。ボタン押下がカードタップへ伝播して別ポップアップを開くことを禁止する。非表示内容は読み上げない。Compose testで通常ヘッダーと開示中ヘッダーの投稿情報の位置・書式を比較し、末尾操作以外が一致すること、インラインの日時省略・三行上限、非表示内容の非露出を確認する。

Preview関数はサンプル専用ファイルへ集めず、表示責務を持つComposableと同じファイルに置く。`PostItemBody.kt`の既存Previewと`ReplyPreviewCard.kt`のNG非表示/開示Preview、`PostItemHeader.kt`の通常投稿ヘッダー/末尾操作Previewを使い、既存の日時省略・番号/名前/IDの配置を保つ。未取得範囲行は共通Footerを持つ`ReplyPreviewFooter.kt`のPreviewで確認する。画面幅を狭めたPreviewは設けない。

### 6. 返信先プレビューの内部構成と開示ボタンの外観

`ReplyPreviewCard.kt`にカード外枠と表示分岐を置き、非表示ヘッダー、通常ヘッダー、本文・画像はファイル内のprivate Composableへ分ける。表示状態別のファイルは作らず、NG非表示分岐から投稿モデルの名前・ID・本文・画像を子Composableへ渡さない。

「ほかN件」と未取得範囲は`ReplyPreviewFooter.kt`の共通`ReplyPreviewFooter`で表示する。返信先/返信元の一覧操作文言・背景の差は`previewType`で分岐し、未取得範囲行もこのファイルに含める。`PostItemBody.kt`から使う未取得範囲Composableはinternalのまま保つ。親のColumnでヘッダー/本文の余白付き領域とFooterを兄弟に配置し、開閉でFooterの位置・水平余白を変えない。返信元では未取得範囲を描画しない。

共有開示ボタンの`ReplyTargetDisclosureAction`は`PostItemHeader.kt`に置く。同Composableは`PostItemHeader`、返信先ポップアップ、インラインプレビューから利用し、専用の一関数ファイルを作らない。テーマの`outlineVariant`による1dp枠線を設け、高さ下限、`labelSmall`文字スタイル、色、アクセシビリティ表現、表示/隠す操作を維持する。カード面に新しいタップ動作は追加しない。

これらは内部構造の整理であり、表示順・寸法・内容・状態遷移を変えない。既存の画像共有遷移、クリック/画像操作callback、test tag、semanticsも維持する。

## Implementation Contract

1. 新型・API名を確定する前に上表の実コードと呼出元を確認する。提案名は既存APIだと仮定しない。
2. データフローは`parseAnchors`→取得済み番号と未取得範囲の分離→行別状態分類/選択→本文セグメント→インライン状態カードおよび取得済み候補だけの返信先一覧→ViewModel開示イベントとする。未取得区間は番号列挙せず、ポップアップへ渡さない。
3. NG元データを`posts`から削除しない。`deriveVisibleReplySourceMap`、通知用解決、ID/ツリーと通常リンク用filterを緩めない。
4. 元の`AnnotatedString.subSequence`による注釈offset変換を維持する。表示文字列を単純に連結してリンク位置を再利用しない。
5. 一時開示は同一スレッド・同一レス番号の表示箇所間で共有するが、返信元件数、通知、NG設定、通常リンクの対象選別は変更しない。表示中でも通常プレビューの三行上限を維持する。
6. 型・非自明関数は規約どおりKDoc、長い関数は区分コメントを付ける。`@Preview`関数にはKDocを付けない。
7. 実装完了時にdebug build/unit testを必ず実行し、端末が接続可能ならAndroid testも実行して、結果を`tasks.md`へ記録する。

## Risks / Trade-offs

- [取得済み投稿数が多い] → 取得済み候補だけを列挙し、その処理量は既に表示する投稿集合の数に比例させる。未取得範囲の幅に比例する列挙を禁止する。
- [NG情報がsemanticsや画像ロードから漏れる] → 非表示時に本文部分を生成せず、画像コールバック未実行と本文semantics不在を検証する。
- [共有filterの変更で返信元・通知へNG混入] → 返信先専用方針とイベントを分け、既存除外テストを残す。
- [多行挿入で本文や改行が欠落] → 先頭/途中/末尾/連続/空行付きのoffsetとリンク操作を検証する。
- [未取得範囲の重複や隣接] → 範囲を区間演算で統合し、巨大範囲でもその幅に比例した処理をしない。
- [一レス内の挿入数増加で描画負荷増] → 解析はViewModel派生時に行い、候補は実レス数に制限する。無関係なUI更新時の再解析を検証する。
- [旧change未統合のMODIFIED delta] → 本changeは旧changeへの依存を明記し、旧change→本changeの順で仕様統合する。今回archiveは行わない。

## Testing Strategy

- parser unit: 取得済み上限との交差、巨大範囲、重複・隣接区間統合、非昇順token順、Int最大値、無効構文。未取得幅に比例した列挙をしないこと。
- transformer unit: 提示の5パターン、正常優先、各種安価の元行省略、行ごと独立した取得済み候補・残数・未取得範囲、別行重複、本文offset・空行・全件未取得。
- ViewModel unit: 専用一覧が取得済みNG候補を保持し、候補がすべて未取得になれば閉じること、番号からの最新状態解決、スレッド内のレス番号単位で共有する開示状態、popup close後の保持、NG更新・対象消失時の破棄、別スレッドへの非伝播、通常一覧の既存除外維持。
- Compose instrumentation: 複数挿入順、前後URL/文中安価/長押し、表示と隠すのその場遷移と二重発火なし、NG本文semantics不在と画像ロードなし、未取得範囲のインライン表示と一覧非表示、range-only行の非操作性、48dp・文字拡大、開示プレビュー三行上限、ポップアップ通常レス表示。
- 実装担当の必須コマンド: `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`、端末接続時`mise exec -- ./gradlew :app:connectedDebugAndroidTest`。計画の検証は`openspec validate preserve-reply-target-references --strict`と`git diff --check`。

## Migration Plan

永続データ移行は不要。新型導入、派生処理、専用一覧、画面接続、回帰検証の順で実装する。旧Previewとテストのfixtureを同時に移行し、単数state互換分岐は残さない。ロールバックは本change由来のアプリ差分を戻すことで実施でき、NG設定やDBの復元を必要としない。
