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

新要件では返信先表示だけに状態行を導入する。通常の安価リンク、ID・ツリー・返信元一覧まで一括で除外解除しない。

## Goals / Non-Goals

**Goals:** 行単位で取得済み候補・選択・残数と未取得範囲を扱う。本文分割による元位置とリンク注釈を維持する。NGの一時開示を表示箇所単位で管理し、非表示の内容を漏らさない。

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

`PopupInfo.kt`へ通常一覧と返信先候補一覧を区別する種別および統合済み未取得範囲を追加する。返信先専用のイベント入口を`ThreadRouteViewModel`へ設け、`PostItemBody`→`PostItem`→一覧/画面→`ThreadScaffold`から接続する。新入口は取得済み候補番号と範囲を渡す。既存の`onReplyFromClick`や通常リンク入口を一括置換しない。

`ReplyPopup`は返信先モードに限り、正常レス/NG状態行と未取得範囲状態行を生成する。全件NG・未取得でも作成・保持できるよう、専用入口と`prunePopupStackWithoutRenderablePosts`の判定を分ける。通常モードは現行フィルタを保つ。一覧は選択番号も含む全取得済み候補と統合済み未取得範囲を表示する（「ほか」は選択を除く取得済み候補数）。重複ポップアップ判定には種別・番号順・未取得範囲を含め、表示箇所の一時開示状態は含めない。既存の`popupId`、段数・位置・インデント・スクロール・画像namespaceを維持する。

返信先カード本体から選択レスを単体表示する操作も、同じ専用入口へ選択番号だけを渡す。プレビューで一時開示済みでも、新しいポップアップには開示を引き継がない。未取得範囲状態行には本文を開くカードタップを設けず、取得済み候補の表示ボタンと省略操作だけを提供する。残数ゼロでは「ほか0件」を出さない。未取得範囲だけの行にも状態テキストを表示する。

番号に対応する投稿がない行ではID集計等へ添字アクセスしない。更新で未取得→正常になれば同じ番号の通常レスへ遷移する。自動取得は追加しない。

### 4. 一時開示はUiState所有で表示箇所に閉じる

`ThreadUiState`と`ThreadRouteViewModel`で開示集合とイベントを管理する。キーはプレビューならスレッド識別＋ホスト投稿番号＋安価行の識別＋対象番号、ポップアップなら`popupId`＋対象番号。Composable内の`remember`を唯一の状態所有元としない。

非表示→表示→隠すは同一箇所の遷移とする。プレビューは`ReplyPreviewCard`相当の三行本文・画像表示と隠すボタン、一覧は通常の`PostItem`相当と隠すボタンを描画する。NG設定を書き換えず、新規ポップアップを開かない。非表示時は本文・名前・ID・サムネイルをcompositionへ入れず、semanticsと画像取得も停止する。

ポップアップ閉鎖、スレッド破棄、本文変更で安価行が無効になった場合は対応キーを破棄する。通常スクロールの再構成では開示状態を保持する。NGルール更新時は開示集合をクリアして安全側に戻し、新たにNGになった正常レスも必ず非表示から始める。無関係なstate更新で本文解析を再実行しない既存キャッシュ境界を維持する。

全画面で同番号を一括開示する方式は、意図しない別箇所の内容表示を起こすため採らない。

### 5. 文言・アクセシビリティ・Preview

状態行用部品を`ui/thread/res`へ追加し、プレビューとポップアップで共有する。添付イメージにならい左側に`#番号`と状態、右側にボタンを置き、狭幅・文字拡大では折り返す。これはNG状態行の番号表記であり、正常カードの数字始まりヘッダーは変えない。

`strings_reply_preview.xml`へ「NGで非表示」「表示」「隠す」と未取得範囲の表示文言を追加する。未取得範囲は非操作テキストとしてカード内の「ほかN件」直下と、返信先一覧の取得済み候補後へ配置する。「ほかN件」は選択を除く取得済み候補だけを数える。

表示/隠すは48dp以上とし、読み上げ名で対象番号と操作を伝える。状態・ボタン・一覧操作・画像操作を独立フォーカスにする。ボタン押下がカードタップへ伝播して別ポップアップを開くことを禁止する。非表示内容は読み上げない。

`PostItemWithReplyPreviewsPreview`と`PostItemBodyWithReplyTargetPreview`を行別stateへ移行する。部品PreviewにはNG非表示、開示と隠す、未取得範囲を用意する。複数挿入と5パターンはCompose testで検証する。

## Implementation Contract

1. 新型・API名を確定する前に上表の実コードと呼出元を確認する。提案名は既存APIだと仮定しない。
2. データフローは`parseAnchors`→取得済み番号と未取得範囲の分離→行別状態分類/選択→本文セグメント→状態カード／返信先専用一覧→ViewModel開示イベントとする。未取得区間は番号列挙しない。
3. NG元データを`posts`から削除しない。`deriveVisibleReplySourceMap`、通知用解決、ID/ツリーと通常リンク用filterを緩めない。
4. 元の`AnnotatedString.subSequence`による注釈offset変換を維持する。表示文字列を単純に連結してリンク位置を再利用しない。
5. 一時開示によって返信元件数、通知、他箇所の開示を変更しない。表示中でも通常プレビューの三行上限を維持する。
6. 型・非自明関数は規約どおりKDoc、長い関数は区分コメントを付ける。`@Preview`関数にはKDocを付けない。
7. 本計画でアプリ変更、ビルド、テストは実行しない。実装担当が下記の検証を実行して記録する。

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
- ViewModel unit: 専用一覧が全件参照不可でも残る、番号からの最新状態解決、表示箇所別開示、隠す・閉鎖時破棄、NG更新・追加取得、通常一覧の既存除外維持。
- Compose instrumentation: 複数挿入順、前後URL/文中安価/長押し、表示と隠すのその場遷移と二重発火なし、NG本文semantics不在と画像ロードなし、未取得範囲のプレビュー/一覧表示、48dp・文字拡大、開示プレビュー三行上限、ポップアップ通常レス表示。
- 実装担当の必須コマンド: `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`、端末接続時`mise exec -- ./gradlew :app:connectedDebugAndroidTest`。計画の検証は`openspec validate preserve-reply-target-references --strict`と`git diff --check`。

## Migration Plan

永続データ移行は不要。新型導入、派生処理、専用一覧、画面接続、回帰検証の順で実装する。旧Previewとテストのfixtureを同時に移行し、単数state互換分岐は残さない。ロールバックは本change由来のアプリ差分を戻すことで実施でき、NG設定やDBの復元を必要としない。
