## 1. 返信解析と通知候補モデル

- [ ] 1.1 `ThreadDisplayTransformers.kt` と `LinkUtils.kt` の既存アンカー規則を共通化する `ReplyAnchorParser` を追加し、`>>(\d+)` の単一・複数・重複・範囲表記を `ReplyAnchorParserTest` で固定する。
- [ ] 1.2 新着境界、自レス番号集合、候補レスから「返信レス単位」の候補を返す純粋な検出クラスを追加し、初回抑止、レス数減少、自レス返信除外、複数自レス参照をunit testで検証する。
- [ ] 1.3 `ThreadDisplayTransformers.deriveReplyMaps()` と本文リンク生成を共通Parserへ切り替え、既存 `ThreadContentLoadUseCaseTest` と関連リンクtestが同じ表示結果を維持することを確認する。

## 2. 通知状態のRoom永続化

- [ ] 2.1 `data/datasource/local/entity` に `(threadId, replyResNo)` 複合主キー、通知payload、`DETECTED/DELIVERED/SUPPRESSED` statusを持つ `ReplyNotificationEntity` を追加する。
- [ ] 2.2 `data/datasource/local/dao` にinsert-ignore、スレッド単位のDETECTED取得、現在statusを条件としたstatus更新を持つ `ReplyNotificationDao` を追加し、競合時の戻り値を検証するDAO testを追加する。
- [ ] 2.3 `ReplyNotificationRepository` を追加し、`DatabaseWriteGate`を使った候補登録・DETECTED取得・条件付きstatus更新を実装して、同一返信の再登録と並行相当操作をrepository testで検証する。
- [ ] 2.4 `AppDatabase.kt` をversion 12へ上げ、Entity/DAO、`MIGRATION_11_12`、`ALL_REGISTERED_MIGRATIONS`を登録し、`AppDatabaseMigrationTest`のunit testとinstrumented testへv11→v12のテーブル・主キー・既存データ保持検証を追加する。
- [ ] 2.5 Room KSPで `app/schemas/.../12.json` を生成し、version 2から12までの復元migration chainが連続することを既存migration testで確認する。

## 3. 設定データと自レス照合入力

- [ ] 3.1 `SettingsRepository` の既存DataStore設定へ初期値falseの返信通知boolean、observe、更新APIを追加し、既存Repository testで初期値と永続更新を検証する。
- [ ] 3.2 `ThreadHistoryRepository` / `PostHistoryRepository` に `ThreadId` から履歴と確定済み自レス番号を一回取得するAPIを追加し、履歴なしでは空集合を返すことをrepository testで検証する。
- [ ] 3.3 `OwnPostReconciliationUseCase` を `ReplyInfo` または照合専用入力モデルから実行可能にし、既存スレッド画面の照合条件を変えず、同じ取得内でpending自レスを確定できることを `OwnPostReconciliationUseCaseTest` で確認する。

## 4. 共通スレッド取得オーケストレーター

- [ ] 4.1 `ui/thread/viewmodel` の既存UseCase配置に `ThreadRefreshRequest`、`ThreadRefreshResult`、`ThreadRefreshUseCase` を追加し、取得前state読取と `DatRepository.getThread()` の成功・失敗を表現する。
- [ ] 4.2 `ThreadRefreshUseCase` に「pending照合→自レス番号再読込→新着候補算出→通知レコード一意登録→最大レス数state更新」の順序を実装し、処理順序と取得失敗時にDBを変更しないことをunit testで検証する。
- [ ] 4.3 通知設定無効時、初回stateなし、取得レス数減少時には候補を登録せず、取得後stateだけを最新化して遡及通知を防ぐことを `ThreadRefreshUseCaseTest` で検証する。
- [ ] 4.4 同じ返信を二回取得した場合と二つの呼び出しが同じ取得前境界を使う場合に、新規通知レコードが一件だけになることをUseCase/Repository testで検証する。

## 5. スレッド画面とタブ画面の共通処理接続

- [ ] 5.1 `ThreadContentLoadUseCase` の取得元を `DatRepository` から `ThreadRefreshUseCase` へ切り替え、共通結果から従来の `ThreadContentLoadResult` と進捗通知を構築して `ThreadContentLoadUseCaseTest` の既存派生データを維持する。
- [ ] 5.2 `ThreadRouteViewModel.loadThreadContent()` から共通処理後に重複して行われる自レス照合・thread state更新を除去し、履歴アクセス、既読位置、表示用状態、更新グループだけを画面固有処理として維持することを `ThreadRouteViewModelTest` で検証する。
- [ ] 5.3 `ThreadTabsCoordinator.refreshOpenThreads()` の直接 `DatRepository.getThread()` 呼び出しを `ThreadRefreshUseCase` へ置き換え、レス数・進捗・失敗時の既存挙動を `ThreadTabsCoordinatorTest` で維持する。
- [ ] 5.4 スレッド画面更新後のタブ更新、および逆順の更新をテストし、どちらも同じ返信を一度だけ登録することを確認する。

## 6. Android通知Publisherとチャネル

- [ ] 6.1 Android非依存の `ReplyNotificationPublisher` と投稿結果型を追加し、成功・権限なし・システム無効・一時失敗を区別できるFake付きunit testを作成する。
- [ ] 6.2 `NotificationManagerCompat`を使うPublisher実装を追加し、stable notification ID、small icon、タイトル、レス番号、本文プレビュー、`setAutoCancel(true)`をRobolectricまたはinstrumented testで検証する。
- [ ] 6.3 `AndroidManifest.xml` に `POST_NOTIFICATIONS` を追加し、`SlevoApplication.onCreate()` でAPI 26以上に高重要度の返信通知チャネルを冪等作成し、API 24-25ではCompat priorityだけを使う。
- [ ] 6.4 対象スレッドURLの `ACTION_VIEW` Intentを `MainActivity`へ直接渡す `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` のPendingIntentを実装し、既存 `DeepLinkHandler` が対象タブを登録・選択する遷移testを追加する。
- [ ] 6.5 `ThreadRefreshUseCase` でスレッド内のDETECTEDレコードをPublisherへ渡し、成功時DELIVERED、通知不能時SUPPRESSED、一時失敗時DETECTED維持と次回再試行をunit testで検証する。

## 7. 一般設定と通知権限UI

- [ ] 7.1 `SettingsViewModel` / `SettingsUiState` に返信通知状態と権限結果イベントを追加し、許可時だけtrue、拒否時false、OFF操作でfalseを保存することを `SettingsViewModelTest` で検証する。
- [ ] 7.2 `SettingsGeneralScreen` に「返信通知」スイッチと説明文を追加し、API 33以上のOFF→ONで `ActivityResultContracts.RequestPermission()` を起動し、API 32以下では直接有効化する。
- [ ] 7.3 一般設定Composableの `@Preview` を更新し、Compose testで文言、Switchのchecked state、click semantics、権限許可・拒否後の表示を確認する。
- [ ] 7.4 通知権限やシステム通知が無効な状態で返信を取得してもスレッド更新が成功し、対象レコードが後から大量通知されないことを結合相当testで確認する。

## 8. 最終検証

- [ ] 8.1 新規・変更した全class/interface/data class/object/enumのKDoc、非自明関数のKDoc、30行超関数のセクション見出しを確認し、`@Preview`関数にKDocがないことをレビューする。
- [ ] 8.2 `./gradlew testDebugUnitTest` を実行し、既存・追加unit testが全件成功するまで修正する。
- [ ] 8.3 `./gradlew assembleDebug` を実行し、API 24以上・targetSdk 35のManifest、Room、Hilt、Composeを含むdebug buildが成功するまで修正する。
- [ ] 8.4 Android実行環境で `./gradlew connectedDebugAndroidTest` を実行し、v11→v12 migration、通知チャネル、通知内容、PendingIntent遷移、設定UIのinstrumented test結果を記録する。実行環境がない場合は未実行理由とCIで必要な確認を明記する。
