## Context

現在、スレッド画面は `ThreadRouteViewModel.loadThreadContent()` から `ThreadContentLoadUseCase.load()` を呼び、全レスを表示用モデルへ変換した後、履歴記録と `OwnPostReconciliationUseCase` による自レス照合を行う。タブ画面の `ThreadTabsCoordinator.refreshOpenThreads()` は同じ `DatRepository.getThread()` を直接呼ぶが、取得結果からレス数だけを取り出して本文を破棄する。そのため、取得処理自体は共通でも、取得後にレス本文を扱える共通境界がない。

確定済み自レス番号は `post_histories` に保存される。返信関係は永続化されず、`ThreadDisplayTransformers.deriveReplyMaps()` と `LinkUtils` がそれぞれ `>>(\d+)` を解析している。通知チャネル、通知権限、OS通知Publisher、通知済み返信の永続状態は存在しない。Roomはversion 11で、schema exportと連続migrationが必須である。

## Goals / Non-Goals

**Goals:**

- スレッド画面とタブ画面が同じ取得オーケストレーターを使用し、返信判定順序と重複防止を一元化する。
- 自レス照合、返信検出、通知レコード登録、客観的スレッド状態更新を、取得成功後の一つの共通パイプラインとして実行する。
- OS通知という外部副作用とDB状態を分離し、並行更新や再起動でも同じ返信を重複表示しにくい構造にする。
- Android API 24からtargetSdk 35までの通知要件に対応する。

**Non-Goals:**

- WorkManager、Service、AlarmManager等によるバックグラウンド取得。
- `>>1-5`、`>>1,3` 等の新しいアンカー文法。
- 通知対象レスへのスクロールやレス番号付きDeep Linkの新設。
- 既存のスレッド表示用派生データ、既読位置、更新グループの意味変更。

## Decisions

### 1. `DatRepository`の上に共通取得オーケストレーターを追加する

`DatRepository.getThread()` は通信、差分取得、dat解析に限定したままにする。その上に、仮称 `ThreadRefreshUseCase` を `ui/thread/viewmodel` ではなく複数画面から利用できるUseCase層へ追加する。

```text
ThreadRouteViewModel ─ ThreadContentLoadUseCase ─┐
                                                ├─ ThreadRefreshUseCase.refresh()
ThreadTabsCoordinator ───────────────────────────┘      ├─ 取得前ThreadStateの読取
                                                       ├─ DatRepository.getThread()
                                                       ├─ pending自レス照合
                                                       ├─ 自レス番号再読込
                                                       ├─ 新着返信検出・一意登録
                                                       └─ ThreadStateの最大レス数更新
```

入力は `boardUrl`、`boardId`、`boardName`、`threadKey`、既知のタイトルを持つrequestとし、両経路が同じメタデータを渡す。戻り値は取得した `List<ReplyInfo>`、取得タイトル、取得前レス数を保持する。スレッド画面の `ThreadContentLoadUseCase` はこの戻り値から従来どおり表示用の `ThreadContentLoadResult` を構築し、タブ画面はレス数と成功状態だけを使う。

代替案として両呼び出し元へ通知コードを追加する方法は、タブ側だけ自レス照合が抜けること、処理順序が分岐すること、競合テストが重複することから採用しない。`DatRepository`自身へ通知副作用を入れる方法も、データ取得責務とユーザー設定・DB・Android通知が混在するため採用しない。

### 2. 自レス照合を通知判定より先に共通実行する

`OwnPostReconciliationUseCase` が現在必要とする表示用モデルへの依存を分離し、dat取得結果から照合に必要なレス番号、本文、日時、ID、名前、メールを渡せる入力モデルまたは `ReplyInfo` ベースの入口を追加する。スレッド履歴が存在し、同一scopeにpending投稿がある場合だけ照合する。

処理順序を「pending照合→`PostHistoryRepository`から自レス番号を再読込→返信判定」とする。これにより、同一取得結果内で確定した自レスも返信先候補になる。スレッド履歴が存在しない場合は自レス履歴も成立しないため照合と通知判定を空集合として継続し、取得自体は失敗させない。

### 3. アンカー解析を純粋な共通Parserへ集約する

仮称 `ReplyAnchorParser` を追加し、本文中の `>>(\d+)` を出現順に抽出して重複を除いた正のレス番号集合を返す。`ThreadDisplayTransformers.deriveReplyMaps()` と通知検出は必ずこのParserを使う。`LinkUtils`のAnnotatedString生成も同じ正規表現定義を参照できる形にし、解釈差を防ぐ。

範囲表記は展開しない。`>>1-5` は既存仕様どおり `>>1` 部分だけが単一アンカーとして認識され得るため、「範囲全体を展開しない」ことをテストで固定する。

### 4. 取得前の客観状態を新着境界にする

`ThreadRefreshUseCase.refresh()` はネットワーク取得前に `ThreadStateRepository.getThreadState(threadId)` を読み、`latestResCount` を取得前境界として保持する。候補範囲は `previousResCount + 1..posts.size` とする。

状態が存在しない場合は `posts.size` を境界として扱い、その取得では通知候補を作らない。通知設定が無効でも取得後に従来どおり `thread_states.latestResCount` を更新するため、再度有効にした時点で無効期間中のレスは新着候補にならない。取得レス数が保存値より小さい場合も通知候補を作らず、既存の最大値保持規則を維持する。

並行する二取得が同じ境界を読む可能性は許容する。最終的な重複排除はDBの一意制約で保証する。

### 5. 返信レス単位の永続レコードを追加する

Room versionを12へ上げ、仮称 `ReplyNotificationEntity` と `ReplyNotificationDao` を追加する。テーブルは最低限次を保持する。

- `threadId` と `replyResNo` の複合主キー
- `targetOwnResNumbers`（表示・診断用に安定した文字列表現で保存）
- `boardUrl`、`threadKey`、`threadTitle`
- `messagePreview`
- `detectedAt`
- `status`: `DETECTED`、`DELIVERED`、`SUPPRESSED`

DAOは `@Insert(onConflict = IGNORE)` の戻り値で新規登録だけを識別し、status更新は現在値を条件にした更新件数で競合を判定する。Repositoryは `DatabaseWriteGate.withWritePermit` と `AppDatabase.withTransaction` の既存慣例に従う。`MIGRATION_11_12`、`ALL_REGISTERED_MIGRATIONS`、`12.json`を追加し、復元可能version 2からのmigration chainを切らさない。

一返信が複数の自レスを参照しても主キーは返信レス番号単位なので一レコードになる。検出済みレコードはスレッドのレス数減少やタブ閉鎖で削除しない。

### 6. DB登録とOS通知投稿を分離する

Android依存を持たない `ReplyNotificationPublisher` interfaceと、`NotificationManagerCompat`を使う実装を追加する。共通UseCaseは候補をDBへ一意登録した後、そのスレッドに残る `DETECTED` レコードを読み出してPublisherへ渡す。これにより、前回の一時失敗も次回のフォアグラウンド再取得で再試行する。Publisherの結果に応じて次のように遷移させる。

- 投稿成功: `DETECTED → DELIVERED`
- 権限なし、システム通知無効、利用者設定無効: `DETECTED → SUPPRESSED`
- 一時的な予期しない投稿失敗: `DETECTED`を維持し、次回のフォアグラウンド再取得で再試行

通知IDは `threadId + replyResNo` から決定的に生成する。同時Publisher実行や「OS通知投稿成功後、DB更新前」のクラッシュで再投稿されても、同じ通知を置換する。Exactly-onceの外部副作用は保証できないが、永続一意性と安定IDでユーザー可視の重複を抑える。

### 7. Android通知とタップ遷移は既存Deep Linkを利用する

`SlevoApplication.onCreate()` からAPI 26以上で返信通知チャネルを冪等に作成する。重要度は返信を即時に認識できる `IMPORTANCE_HIGH` とし、API 24-25では `NotificationCompat.PRIORITY_HIGH` を使う。通知にはsmall icon、スレッドタイトル、「レス n」、本文の改行と過剰空白を整えた短いプレビューを設定する。

content intentは既存が解決可能なスレッドURLを持つ `ACTION_VIEW` Intentで `MainActivity`を直接開き、`FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` を指定する。BroadcastReceiverやService経由のnotification trampolineは作らない。`setAutoCancel(true)` とする。既存 `DeepLinkHandler` がタブ登録・選択・スレッド画面遷移を担当し、レス位置は既存スクロール状態に委ねる。

### 8. 設定スイッチを権限要求の起点にする

`SettingsRepository`へ返信通知booleanを追加し、初期値をfalseとする。`SettingsViewModel` / `SettingsUiState` と `SettingsGeneralScreen`へ「返信通知」スイッチを追加する。

API 33以上でOFFからONへ変更するとき、Composableは `ActivityResultContracts.RequestPermission()` で `POST_NOTIFICATIONS` を要求する。許可結果をViewModelへ渡し、許可時だけRepositoryへtrueを保存する。拒否時はfalseを維持する。API 32以下は直接trueへ変更する。OS設定で後から通知を無効化された場合は、Publisherが対象レコードを `SUPPRESSED` にして取得を継続する。

## Implementation Contract

実装エージェントは次の契約を維持すること。

1. スレッド画面は `ThreadContentLoadUseCase` を介して、タブ画面は `ThreadTabsCoordinator` から、新しい共通取得UseCaseへ接続する。両経路から `DatRepository.getThread()` を個別に呼んだ後で通知判定してはならない。
2. 共通取得UseCaseの順序は「取得前state読取→dat取得→pending自レス照合→自レス番号再読込→返信候補算出・一意登録→thread state更新→OS通知投稿」とする。取得失敗時はstateと通知レコードを変更しない。
3. スレッド画面固有の表示変換、履歴アクセス記録、既読位置、更新グループは `ThreadRouteViewModel` / `ThreadContentLoadUseCase`側に残し、タブ一括更新では実行しない。
4. 新着境界は取得開始前の永続 `thread_states.latestResCount` とし、UIのスクロール位置や `lastReadResNo` を通知判定に使わない。
5. 同じ `(threadId, replyResNo)` を二度登録できないDB制約を必須とし、メモリ内フラグだけで重複防止しない。
6. 自レス集合の再読込前に返信候補を確定しない。候補レス番号自身が自レス集合に含まれる場合は除外する。
7. 全てのアンカー利用箇所は共通Parserと同じ単一番号規則を使う。
8. 通知権限がないことをスレッド取得失敗として扱わない。通知不能レコードを無期限に保留しない。
9. 新規class/interface/data class/object/enumには宣言アノテーションより上へKDocを置き、非自明関数と30行超関数のコメント規約を守る。Compose Preview関数にはKDocを付けない。

## Error Cases and Compatibility

- dat取得失敗・parse失敗: 既存の各画面の失敗表示を維持し、通知処理は行わない。
- 自レス履歴読込失敗・通知DB書込失敗: エラーをログへ記録し、取得したスレッド表示やタブ更新まで不必要に失敗させない。ただしthread stateだけ先に進めると通知機会を失うため、通知設定有効時の通知DB処理とstate更新の順序・transaction境界をRepositoryテストで固定する。
- OS通知投稿失敗: 予期しない例外だけ `DETECTED` を維持し、次のアプリ操作時に再試行する。権限なしは `SUPPRESSED` とする。
- API 24-25: 通知チャネルAPIを呼ばず、Compat priorityを使用する。
- API 26-32: チャネル必須、ランタイム通知権限要求なし。
- API 33以上: Manifest宣言とランタイム権限確認を必須とする。
- PendingIntent: targetSdk 35に合わせてimmutableを明示し、同じ返信のintent extrasが衝突しないrequestCodeを使用する。

## Testing Strategy

- 純粋unit test: `ReplyAnchorParser`の単一・複数・重複・範囲表記、返信候補の境界、自レス返信除外、初回抑止。
- UseCase unit test: `ThreadRefreshUseCase`の成功順序、取得失敗、pending確定後判定、通知無効、state減少、二経路相当の再実行。MockK、`runTest`、既存 `MainDispatcherRule` の慣例に合わせる。
- Repository unit/instrumented test: insert-ignore、status条件更新、複合主キー競合、transaction失敗時、v11→v12 migration、v2からv12までのmigration chain。
- ViewModel/Coordinator unit test: `ThreadRouteViewModelTest` と `ThreadTabsCoordinatorTest` で共通UseCaseが呼ばれ、直接取得と通知処理が重複しないことを検証する。
- Settings unit/Compose test: 初期OFF、API 33権限許可・拒否、OFF操作、表示文言とSwitchのaccessibility semantics。
- 通知実装test: チャネル属性、stable notification ID、immutable PendingIntent、通知内容、権限なしの抑止。Android API依存部分はRobolectricまたはinstrumented test、Publisher interfaceはFakeでunit testする。
- 遷移test: 通知用スレッドURLが既存 `DeepLinkHandler` によりタブ登録・選択されることを既存navigation testへ追加する。

実装完了時はリポジトリ要件に従い `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` を実行し、追加したmigration instrumented testは実行可能なAndroid環境で `./gradlew connectedDebugAndroidTest` の対象として確認する。

## Migration Plan

1. `ReplyNotificationEntity`を含むRoom version 12 schemaと `MIGRATION_11_12` を追加する。
2. migration unit testとinstrumented testで既存データ保持と空の通知テーブル作成を検証する。
3. 設定初期値falseでリリースし、既存利用者へ権限ダイアログを自動表示しない。
4. 利用者が設定を有効化した後のフォアグラウンド取得だけ通知対象にする。

ロールバック時、version 12から11への本番downgrade migrationは提供しない。旧バージョンへ戻すと通常のRoom downgrade制約を受けるため、DBを保持したままのアプリ版ダウングレードはサポート対象外とする。

## Risks / Trade-offs

- [引用として書かれた `>>n` も返信と判定される] → 既存UIと同じ構文解釈を優先し、意味解析は行わないことを仕様とテストで固定する。
- [タブ一括更新で全レス本文を走査するCPUコスト] → 新着境界より後のレスだけアンカー解析し、表示用ツリー等の派生データはタブ側で生成しない。
- [二経路の同時取得で候補判定が重なる] → 複合主キーとinsert-ignore、stable notification IDで吸収する。
- [OS通知とDB更新を完全な一トランザクションにできない] → 状態機械とstable IDによる再試行で、通知消失とユーザー可視重複の双方を抑える。
- [スレッド表示中にもheads-up通知が出て煩雑] → 今回は画面種別で挙動を変えず、要件どおり両取得経路を同一に扱う。
- [Room migration追加がバックアップ復元へ影響する] → migration chain検証とschema exportを必須タスクにする。
