# Design

## Context

目的は`proposal.md`を参照。以下のソースパスは、特記がなければ`app/src/main/java/com/websarva/wings/android/slevo/`からの相対パス。

### 現状と根拠

| 箇所 | 現在の動作・制約 |
| --- | --- |
| `data/util/ReplyAnchorParser.kt:10–20` | `>>(\\d+)`で数字部分だけを抽出。範囲・カンマは先頭番号に縮退する。既存テストも範囲非展開を固定している。 |
| `ui/util/LinkUtils.kt:20,67–79` | パーサの正規表現を共有するが、注釈値は単一番号の`group(1)`。複数安価全体のリンクではない。 |
| `ui/thread/viewmodel/ThreadDisplayTransformers.kt:35–98` | 全投稿から`replySourceMap`を生成。ツリー親は本文が`>>`で始まる場合の最初の参照番号一つ。 |
| `ui/thread/viewmodel/ThreadRouteViewModel.kt:981–1006,1131–1160` | NGの正規表現コンパイルとレス判定がprivate関数。NG除外は取得元の投稿集合を破壊せず、画面合成時に実施する。 |
| `ui/thread/screen/components/ThreadPostListContent.kt:139` | NG除外前の返信元番号をヘッダーへ渡す。返信数の不具合の原因。`ReplyPopup.kt:629`は既にNGを除外している。 |
| `ui/thread/state/ThreadUiState.kt:19–22,58` | `sortType`は`NUMBER`/`TREE`。本体は同じ`PostItem`を使い、返信プレビュー専用のデータ・表示分岐はない。 |
| `data/repository/BoardRepository.kt:225–232` | 板の`BBS_NONAME_NAME`取得が可能だが、既存用途は投稿時の名前プレースホルダ。レス描画には未接続。 |
| `data/notification/ReplyNotificationCandidate.kt:30–55` | 初回・件数減少・自レス除外・複数自レスの一候補化は実装済み。NG判定はない。 |
| `ui/thread/viewmodel/ThreadRefreshUseCase.kt:43–68,124–201` | 両取得経路が共有する、取得→自レス照合→返信検出→候補保存→配信の境界。`DETECTED`の再処理もここにある。 |
| `data/repository/NgRepository.kt:34` | 読み取りは`observeNgs(): Flow<List<NgEntity>>`。通知側へ未注入。 |

関連する既存仕様は`reply-notification`、`thread-load-state`、`thread-tree-popup`、`thread-response-order`、`image-viewer`。後四者の既存契約は維持するため、本変更のdeltaは新規二能力と`reply-notification`だけとする。`thread-derived-info`はスレ作成日時・勢いの計算であり、個々のレスの返信件数の能力とは異なる。

## Goals / Non-Goals

**Goals:**
- 安価の構文解析を一元化し、UI表示、リンク、返信通知の参照先を一致させる。
- NG判定をUIモデルに依存しない共通処理にし、画面未生成のタブ更新でも利用する。
- 表示件数とクリック先を同じNG除外済み集合から生成し、プレビューだけに件数上限を適用する。
- 本文描画に必要な構造・プレビュー情報をViewModel所有のUiStateとして用意し、Composablesは描画・座標取得・イベント通知に限定する。

**Non-Goals:**
- ツリー表示へのインラインプレビュー、プレビューの再帰展開、通常レスの三行化。
- ツリーの複数親化、更新グループ・新着バー・既読境界の変更、NG後のレス番号の振り直し。
- 配信済み通知の取り消し、過去レスの遡及通知、バックグラウンド取得、通知設定追加。
- 新たな画像拡張子対応、画像ビューアの別実装、板タイトルNGをレスNGとして扱う変更。
- DBスキーマ・バックアップ形式の変更。

## Decisions

### 1. 安価は位置付きの構造として解析する

`ReplyAnchorParser`を唯一の構文定義元にする。以下の名前は**追加する型/APIの提案名**であり、実在APIではない。

- `ReplyAnchorToken`: 元本文の開始・終了offset、元表記、単一番号または番号区間のリスト。区間の両端は正の`Int`。
- `parseAnchors(content)`: 出現順のtokenを返す。半角`>>`・数字・`-`・`,`を扱う。`>>1-3,5`の混合を許可し、同一tokenの無効な項目はtoken全体を無効にする。
- `resolveReferencedNumbers(tokens, maxPostNumber)`: 各区間を`1..maxPostNumber`と交差させてから列挙し、出現順を保って重複除去する。上限なしで範囲を展開するAPIは作らない。空投稿集合では空を返す。
- `extractReferencedNumbers`の既存呼び出しを、必要な上限を明示するAPIへ移行する。通知は`posts.size`、返信逆引きも同じ上限を用いる。

正規表現の先頭だけを一致させて不正tokenを有効化してはならない。特に`>>1-`、`>>1,`、逆順、0、数値overflowをテストする。既存の`ANCHOR_PATTERN`を直接使う`LinkUtils`はtokenのspanへ移行し、`group(1)`による単一番号の仮定を削除する。

通常本文の複数安価タップはtoken全体をリンクにし、解決済み番号群をViewModelへ渡す。既存`addPopupForReplyNumber`を単数ラッパーとして維持し、複数番号を受ける新しい入口（提案名`addPopupForReplyNumbers`）で範囲確認・NG除外・重複除去・空集合ガードを共通化する。本体と`ReplyPopup`のコールバック配線も両方更新する。プレビューは安価行だけが対象だが、逆引きと通知の参照抽出は本文全体が対象である。

ツリー親は従来どおり「本文先頭が`>>`で始まる場合の最初の有効な構文上の番号」一つとし、その番号に既存の存在確認・親条件を適用する。範囲内の別番号を親へ繰り上げたり、複数親にしたりしない。たとえば`>>999 >>1`の先頭が範囲外でも、後続の1を新たな親に採用しない。

**代替案:** UI専用パーサの追加は通知との乖離を残す。単純な正規表現置換は不正表記の部分一致と巨大範囲展開を防げないため採用しない。

### 2. NG判定を共通化し、表示用返信マップを分ける

`compileNgs`/`deriveNgPostNumbers`の処理を、UI型・Composeに依存しない`data/util`の純粋なmatcherへ抽出する（提案名`PostNgMatcher`）。入力は板ID、コンパイル済みルール、レスのID・名前・本文。`ReplyInfo`と`ThreadPostUiModel`をそれぞれ同じ正規化済み値へ対応付ける。

- 全板`boardId == null`と同一板だけを判定する。
- `USER_ID`、`USER_NAME`、`WORD`の既存の`containsMatchIn`、リテラル時の`Regex.escape`を維持する。
- `THREAD_TITLE`はレス判定対象外とする。正規表現のコンパイル失敗時は既存`compileNgs`の扱いを確認して同じ挙動を抽出し、UIと通知に別々の解釈を作らない。
- 取得データにはNGレスを残す。通知境界、絶対レス番号、既存のツリー構造を変えない。

全件の`replySourceMap`は構造情報として維持し、画面合成時に`visibleReplySourceMap`（**追加フィールド案**）を生成する。値は存在確認・重複除去・NG除外後の番号昇順リスト。`ThreadPostListContent`、`PostItemHeader`、`ReplyPopup`の返信元操作、返信プレビュー、`ThreadVisiblePostsUseCase`の返信数利用箇所へ同じ集合を渡す。0件時の件数リンクは現在のヘッダー契約どおり非表示。プレビューの`take(3)`をこのマップに適用してはならない。

NGのFlow更新でこの表示マップとプレビューを再計算する。スクロール位置など無関係のstate更新で本文全体の再解析・全プレビュー再生成を行わず、投稿版・NGルール・板デフォルト名の変更を再計算の入力にする。

**代替案:** 本体の`:139`でのみ`filterNot`する修正は小さいが、件数・一覧・プレビュー・他表示箇所の分岐が残るため採用しない。生のマップをNGで破壊する方式もNG解除の復元とツリー情報を複雑化するため採用しない。

### 3. プレビュー専用の不変UiStateを生成する

`ui/thread/state`にプレビュー・本文セグメント用の型を追加し、`ThreadUiState`から投稿番号で引けるようにする。以下は**モデル構成案**。

- `PostReplyPreviewState`: 返信先の挿入行、単一一体型/複数分離型、候補番号全体、選択した返信先一件、返信元の先頭三件、各残数。
- `ReplyPreviewUiModel`: 対象番号、表示名、ID、画像URL除去済み本文、同一レスの全画像URL、先頭サムネイルURL、追加枚数。プレビュー内に子プレビューフィールドは持たせない。
- `PostBodySegment`: 元本文offsetを保つ通常テキスト範囲と、挿入対象の安価行。データは元本文から導出し、通常表示用の`posts`を加工して保存し直さない。

生成は`ui/thread/viewmodel`の専用純粋transformer（提案ファイル`ThreadReplyPreviewTransformer.kt`）へ分離する。

1. `\n`単位に行の元offsetを保持する。行全体が安価tokenと空白だけか判定する。画像URL除去はこの判定より後のプレビュー本文だけへ適用する。
2. 対象行の参照先を本文順に解決し、存在確認・NG除外・重複除去後の全候補を保持する。先頭一件とその出現行だけを選択する。文中安価の参照先は返信先プレビュー候補に混ぜない。
3. `>>n`一つだけの行を一体型、範囲・カンマ・複数tokenの行を分離型とする。`>>1-1`や`>>1,1`も表記が複数形式なら分離型。
4. 返信元は`visibleReplySourceMap[postNumber]`から先頭三件を取る。NG除外は上限より先に行う。
5. 返信先の残数は全候補数−1、返信元は全件数−表示数。候補が一つ以下/三つ以下なら省略操作を出さない。返信先「ほか○件」は選択したプレビュー枠の下端に一度だけ配置する。

板デフォルト名は既存の`fetchBoardNoname`を板単位で非同期取得してViewModelのUiStateに保持する。既存の取得結果を再利用できるなら共有し、新規の場合もレス単位では取得しない。レス名と板設定を同じHTML除去・文字参照復元済み表記にして、前後空白除去後に完全一致する場合だけ隠す。独自名・トリップは残し、設定が不明・空・取得失敗なら名前を残す。投稿ダイアログの`namePlaceholder`をプレビュー都合で書き換えない。

画像は既存`extractImageUrls`の認識対象と順序・重複の扱いに合わせる。同じ認識結果のURL spanだけを除去し、非画像URLや前後の文字を壊さない。画像URLだけの行の不要な空行を除き、残りの改行は保つ。正規表現の独自拡張は行わない。

**代替案:** Composable内で候補選択と名前・画像加工を行う方式は状態所有規約に反し、再コンポーズ負荷も高いため採用しない。

### 4. 軽量な専用部品と本文挿入を追加する

新規部品は`ui/thread/res`へ置く（提案名`ReplyPreviewCard`、`PostReplyPreviews`）。`PostItem`へ省略モードを増やすのではなく、専用UiModelを描画する。ヘッダーと本文の縮小倍率は各既存倍率の**0.9倍**を初期値とし、端末fontScaleを打ち消さない。本文は`maxLines = 3`と末尾ellipsis。ヘッダーは別枠で折り返しを許可し、日付・メール・BE等は追加しない。空の名前・IDは空ラベルを描かない。

```
[返信先の単一安価見出し >>1]  または  [返信元]
┌──────────────────────────────┐
│ 1  独自名  ID:xxxx           │ ← ヘッダー全幅
│ 本文の最大三行……      ┌─────┐│
│ 本文……                │画像 ││ ← ヘッダーの下
│                       │ +2  ││
│                       └─────┘│
│ ほか2件                     │ ← 必要な場合だけ
└──────────────────────────────┘
```

- 画像は初期値56.dp四方、本文との間隔8.dp。親幅を超えないようサイズを制限し、画像がないときは`Row`の画像領域・間隔を作らない。画像だけなら空の本文三行分を確保しない。
- 単一安価の`>>n`見出しとカードを共通の背景・輪郭で連結する。同じ元行を別テキストとして重複描画しない。複数形式は元安価行の下に4.dpの間隔で独立した枠を置く。
- 返信元群には「返信」の見出しを置き、各カードと残数を同じグループとして認識できる薄い境界/背景で区分する。色だけに依存しない。
- プレビュー本文内のリンクは独立操作にせず、本文・ヘッダーは対象レスを開く一つの操作とする。画像・「ほか○件」は別操作。カードへの親子clickable重複による二重発火を避ける。
- 本体の`ThreadPostListContent`から、`sortType == ThreadSortType.NUMBER`のときだけプレビューstateを渡す。`ReplyPopup`とツリー順は未指定/nullを渡す明示的な契約にする。
- 挿入後の通常本文テキストでは元の注釈offsetをセグメント先頭基準へ変換する。`PostItemBody`のURL/REPLYのヒットテスト、長押しの元レス内容、押下表示を保つ。後続本文をプレビュー本文と取り違えない。
- 本文分割で外側`LazyColumn`の項目やkeyは追加・変更しない。プレビューは同じ投稿行内に描画し、既存の更新グループ、新着バー、dimmed親のidentityを維持する。

文字列リソース案は「返信」「ほか%d件」「+%d」、読み上げは「レス%dを表示」「画像%d枚中1枚目、ほか%d枚」「返信先の一覧を表示」/「返信の一覧を表示」。操作領域は48.dp以上、TalkBack/キーボードのフォーカス順はヘッダー・本文の一操作→画像→省略操作とする。新規Composableには通常、画像なし、画像複数、長文、文字拡大の`@Preview`を用意する。

**代替案:** 既存`PostItem`の条件分岐を増やす方式はメール・日付・メニュー・通常画像グリッドまで巻き込みやすいので採用しない。

### 5. サムネイルは既存の画像状態と遷移を再利用する

`ImageThumbnailGrid`の単一画像描画・成功時遷移判定・失敗表示を共通の小さな部品へ抽出して通常グリッドと共有する。三列グリッドの一要素を無理に56.dpの枠へ流用するのではなく、単一サムネイルを直接描画できるようにする。

- 全画像一覧はUiModelへ保持し、`onImageClick(firstUrl, allImageUrls, 0, namespace)`で既存のコールバックへ渡す。`buildImageViewerRoute(allImageUrls, 0, namespace)`→既存のナビゲーションを使う。サムネイル用の一枚リストをビューアに渡さない。
- `ImageSharedTransitionKeyFactory`へプレビュー用namespace生成を追加する（提案API`replyPreviewNamespace`）。入力は既存投稿行stable key、返信先/返信元の種別、プレビュー対象番号とし、通常レス・他投稿のプレビュー・同じ画像URLの別出現を区別する。factoryの`buildKey`は遷移元とビューアで共通利用する。
- `enableSharedElements`を本体から同じように伝播し、ポップアップ表示中に本体の共有要素だけを有効化しない。既存routeの`transitionNamespace`で引き継げるため、新たなroute形式は不要。
- `imageLoadFailureByUrl`、`imageLoadingUrls`と`onThreadImageLoadStart/Error/Success/Retry`を再利用する。失敗状態をプレビュー専用のローカルstateへ複製しない。
- 現在の404/410は恒久エラーで再試行しない扱いを維持し、再試行可能な失敗だけ既存どおり明示リロードする。表示成功前にはビューアへ遷移しない。

**代替案:** 新たな画像ローダー・独自の失敗状態は挙動差を生む。通常投稿と同じnamespaceを使う方式は同一画像の同時表示で衝突するため採用しない。

### 6. 通知のNGチェックを共有の取得後処理に置く

画面とタブ一括更新はどちらも`ThreadRefreshUseCase.refresh`を通る。ここへ`NgRepository`を注入し、`observeNgs().first()`でルールを取得する。UIが生成されていることを前提にせず、共通matcherで`ReplyInfo`と`request.boardId`から判定する。

```
dat取得 → 自レス照合 → 確定済み自レス取得
       → NGルールのsnapshot → 共通安価解析 → NG/自レス除外
       → 候補保存 → 配信待ち読出し → 配信直前のNG再確認
       → publish または SUPPRESSED → 条件付きstatus更新
```

`ReplyNotificationDetector.detect`は純粋関数のまま、除外対象の絶対レス番号集合などを入力として受ける。全投稿と取得境界を渡す既存処理は維持し、NGフィルタで投稿を詰めたリストを渡してはならない。候補の自レス集合は参照順で重複除去し、返信元一レスにつき一候補とする。

配信時は`processDetectedNotifications`へ当該取得結果と板情報を渡す。`findDetected`が返す過去の待機候補も最新投稿番号で元レスを解決し、現在のNG snapshotを確認してから`publisher.publish`を呼ぶ。NGなら既存`SUPPRESSED`への条件付き遷移で終える。既に`DELIVERED`の通知には触れない。snapshotは配信ループ開始時に再取得し、その後にルールが変化する厳密な同時性までは保証しない。

NGルールの読み取りに非キャンセル例外が起きた場合はその取得分の新規通知処理をスキップし、配信待ちは提示しない。元レスを解決できない配信待ちも提示せず抑止する。スレッド内容と取得済み最新番号は正常更新し、NG解除や障害回復後に過去レスを再検出しない。失敗は既存`AppLogger`へ内容本文を含めず記録する。`CancellationException`は再throwする。DBの複合主キー、`insertIgnore`、write gate、既存のstatus遷移を維持し、新しい通知保存テーブルや状態は追加しない。

**代替案:** ViewModel側だけのNG除外ではタブ一括更新と待機通知を防げない。publisherへ生のNGルールを持ち込む方式はAndroid提示層にレス判定を重複させるので採用しない。

## Implementation Contract

1. 実装開始前に現行の上記ファイルとテストを読み、提案した新規名と実在APIを区別する。既存動作を変更するのは三つのdeltaで定義した範囲だけ。
2. parser→共通NG→表示用マップ→プレビュー派生→UI・画像→通知統合の順で小さく実装する。各段階に純粋ユニットテストを追加する。
3. `ThreadUiState`を画面状態の所有先とし、ComposableでNG判定・参照選択・本文加工・板設定の取得をしない。共有要素や測定座標などComposeランタイム値だけをUIローカルにする。
4. 番号の解決は絶対番号と`getOrNull(number - 1)`を使い、表示行indexと混同しない。NG本文や画像を空枠・省略数・クリック先から再露出させない。
5. 全新規型へKDoc、非自明関数へ短いKDoc、長い関数へ区分コメントを付ける。KDocはアノテーションの上。`@Preview`関数にはコメントを付けない。
6. 実装完了条件は`tasks.md`に記載した自動検証を満たすこと。実機・実データを必要とする追加確認は、対象環境が利用できる場合の補助確認とする。この計画作成ではアプリ、テスト、ビルド設定、リソースを変更せず、ビルド・テストも実行しない。

## Risks / Trade-offs

- [NG本文の再露出] → 全件マップと表示用マップを分離し、全操作に後者を使用。通知の候補生成と配信の双方で判定する。
- [巨大範囲・再コンポーズ時の負荷] → 範囲を取得済み上限へ交差してから列挙。解析結果を投稿変更時に生成し、画像リクエストは実際に描画された先頭画像だけにする。全レス×全レスの候補探索を行わず逆引きを使う。
- [本文分割による操作の退行] → 元offsetと文字列を保持し、挿入前後のURL・複数安価・長押しをUIテストする。
- [共有要素キー衝突] → 投稿行stable keyとプレビュー種別・対象番号を含む独自namespaceを使用。同一画像の三箇所同時表示を検証する。
- [NG判定の正規化差] → `ReplyInfo`とUIモデルの同じID・名前・本文を使うmatcher比較テストを置く。
- [文字縮小による可読性・操作性低下] → 0.9倍でも端末文字拡大と48.dpの操作領域を維持し、最大文字拡大・狭幅・TalkBackで確認する。
- [NGルール読取不能時に通知が届かない] → 本文の誤配信防止を優先する。取得自体は成功させ、例外をログに残す。遡及通知による埋め合わせはしない。

## Migration Plan

DB移行は不要。安価の範囲非展開テストと現行通知仕様だけを新しい解釈へ更新し、アーカイブ済みの過去設計は書き換えない。更新後に古い返信を再走査して通知を追加する移行処理は作らない。切り戻しはアプリ実装のリバートで行い、既存通知の一意キーとstatusは引き続き利用する。

## Testing Strategy

- `app/src/test/.../data/util/ReplyAnchorParserTest.kt`: 単一・範囲・カンマ・混合・文中・複数行・重複・不正構文・巨大範囲・offset・取得済み上限。旧範囲非展開テストを置換。
- 共通NG matcherテスト: ID/名前/本文、リテラル/正規表現、全板/該当板/別板、タイトルNGの対象外、UIモデルと`ReplyInfo`の一致。
- `ThreadDisplayTransformersTest`と新規プレビューtransformerテスト: NG先行除外、全件数と表示上限、挿入位置、単一/複数形式、取得結果変化、板デフォルト名不明、画像URLだけの本文。既存ツリー親・更新グループは回帰確認。
- `ReplyNotificationDetectorTest`と取得後処理のテスト: 範囲の途中・カンマの後半の自レス、文中安価、複数自レスの集約、NG、待機通知の抑止、NG読取失敗、両取得入口、初回・件数減少・設定・権限・重複防止。
- `app/src/androidTest/...`のComposeテスト: 本文だけ三行、ヘッダー全幅、画像有無と「+N」、「ほか○件」の遷移、本文挿入後のリンク/長押し、ツリーとポップアップ非表示、TalkBack semantics、単一イベント発火。
- 実装担当が実行するコマンド: `./gradlew :app:assembleDebug :app:testDebugUnitTest`、端末接続時`./gradlew :app:connectedDebugAndroidTest`。CI相当は`./gradlew testCiUnitTest assembleCi --stacktrace`。既存ビルド構成はflavorなし。
- 実機・実データが利用できる場合は、狭幅・横画面・最大文字サイズ・ライト/ダーク、追加取得中のポップアップ、同じ画像の通常レス/返信先/返信元同時表示、画像失敗後のスクロール復帰、長いスレッドの連続スクロールを補助的に確認する。これらの手動確認は自動検証の完了条件に含めない。
