## Context

`PendingRestoreApplier.runIfNeeded()`は`runIfNeededOnIo()`から漏れた`Exception`を捕捉し、`recordStartupRestoreFailureOnIo()`で現在のmarkerを無条件に`FAILED`へ更新する。通常applyは`PREPARED → APPLYING → ROLLBACK_READY → DB_SWAPPED → MIGRATION_PENDING`と進み、`ROLLBACK_READY`確定後だけlive DB置換を開始する。`DB_SWAPPED`以降はdurableなDB rollback snapshotとDataStore rollback snapshotを保持し、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`にも次回起動で再開すべき処理がある。

現在は、`dbSwapper.replaceDbFile()`成功後に`DB_SWAPPED` marker write、`PendingRestoreDataStoreReflector.reflect()`、failure/success result write、または`MIGRATION_PENDING` marker writeが例外を投げると、最後に確定した`ROLLBACK_READY`または`DB_SWAPPED`が`FAILED`へ変わる。次回起動の`FAILED` branchは診断logだけで終了するため、restore済みDBとrestore前または部分反映DataStoreを通常起動へ渡し得る。同じ無条件上書きは`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`のrecovery/finalization中の想定外例外にも適用される。

marker writeは`AtomicPendingRestoreMarkerFile`により直前の確定状態を保持でき、pending cleanupはmarkerを最後に削除する。したがって新statusやmarker fieldを追加せず、汎用例外処理が既存markerの意味を壊さないことが最小の修正となる。

## Goals / Non-Goals

**Goals:**

- `ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`を想定外例外後も保持する。
- 次回cold startが既存のrollback、validation、またはfinalization branchを再開できるようにする。
- 回復可能markerを保持する場合、DB/DataStore rollback snapshot、staging、markerをcleanupしない。
- DB置換後に例外が起きても、restore済みDBとrestore前DataStoreを成功状態として受理しない。
- 状態境界と次回起動を含むdeterministic failure-injection testを追加する。

**Non-Goals:**

- `RestoreStatus`の追加、marker/result JSON schema、Room schema、DataStore formatの変更。
- 汎用例外発生と同じ起動内で同期rollbackを新規実行すること。
- 既存のexpected-error returnを処理する`rollbackAndFail()`、migration validation、completion finalizerの再設計。
- queued中の他のCodex finding、UI、Snackbar文言、restore result文言の変更。

## Decisions

### 1. 汎用例外handlerを確定済みmarker statusに応じて保守的にする

`PendingRestoreApplier.recordStartupRestoreFailureOnIo()`はmarkerを読み、次の分類で処理する。

- `ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`: status、`hadExistingLiveDb`、`includeCookies`などmarker全体を変更せず保持する。
- `PREPARED`、`APPLYING`: live DB置換開始前であることがstate orderingにより保証されるため、既存どおり`FAILED`へ記録できる。
- `FAILED`: terminal stateをそのまま維持する。

`ROLLBACK_READY`を保護対象に含めることが重要である。`replaceDbFile()`成功後かつ`DB_SWAPPED` marker確定前に例外が発生すると、durable markerは`ROLLBACK_READY`のままだからである。`COMPLETED`もrollback対象ではないが、success result writeとpending cleanupを再試行するauthoritative finalization markerなので保持する。

全non-terminal markerを一律保持する案は安全側だが、DBを一切変更していない`PREPARED`/`APPLYING`で恒久的なstartup retryを発生させる。新しい`RECOVERY_PENDING` statusを追加する案はschema compatibilityと全consumer変更を増やすため採用しない。同一起動内でcatchから`rollbackAndFail()`を呼ぶ案は、例外発生源がrollback collaborator自体の場合に再入・二次失敗を複雑化するため採用しない。

### 2. Recovery marker保持時もfailure resultはbest-effortで記録する

既存の`writeResult(success = false, ...)`は診断情報として維持する。ただしresult writeの成功・失敗はmarker classificationを変更してはならず、result write後にpending cleanupを実行してはならない。次回起動の既存recovery/finalizationが最終resultを更新できる。

result書き込みを省略する案はUI挙動を変え、診断能力を下げるため採用しない。本changeでは既存messageを変更しないためUI deltaはない。

### 3. Marker読み書き失敗では直前のatomic stateを信頼する

`readMarker()`または`writeMarker()`が例外を投げた場合は、現行どおりhandler内で二次例外を外へ出さない。atomic marker storeが直前の確定markerを保持するため、handlerはcleanup、artifact削除、別status推測を行わない。marker readが`null`の場合も新markerを合成しない。

ここでmarker write failureを注入するのは`PREPARED`/`APPLYING`から`FAILED`への更新だけである。保護対象statusではhandlerがmarker writeを一度も呼ばないことをtestで確認する。DB置換成功後の`DB_SWAPPED` transition write failureは、handlerによる再writeではなく、atomic storeが直前の`ROLLBACK_READY`を保持する別の境界testとして扱う。

## Implementation Contract

1. `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`の`recordStartupRestoreFailureOnIo()`だけをproduction変更の中心とする。
2. marker status classificationを明示し、`ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`では`fileStore.writeMarker(...FAILED...)`を呼ばない。
3. `PREPARED`、`APPLYING`だけは既存failure reasonで`FAILED`へ更新し、既存`FAILED`は再書き込み不要とする。
4. recovery marker保持後も既存failure result writeをbest-effortで行うが、`cleanupPending()`、rollback artifact削除、quarantineを追加しない。
5. `runIfNeeded()`の`Dispatchers.IO`境界と`SlevoApplication`のstartup safety-net catchを変更しない。
6. `RestoreStatus`、`PendingRestoreMarker`、`AtomicPendingRestoreMarkerFile`、marker/result schemaを変更しない。
7. test fakeへ必要最小限のfailure injectionとartifact/call observationを追加する。production専用のtest hookは追加しない。
8. 追加または変更するnon-trivial functionにはannotationより上へKDocを置き、長いfunctionにはsection commentを付けるrepository規約を守る。

## Error Cases and Compatibility

- `ROLLBACK_READY`で例外: DBが未置換または既に置換済みのどちらでも、次回起動は既存`recoverFromRollbackReady()`を実行し、artifactからrestore前generationへ収束する。
- `DB_SWAPPED`でDataStore反映例外: markerと両rollback snapshotを保持し、次回起動はDB/DataStoreをrollbackする。
- `MIGRATION_PENDING`で例外: markerを保持し、次回起動はversion validation後にfinalizationまたはrollbackへ進む。
- `ROLLBACK_REQUIRED`でrollback例外: markerとartifactを保持し、次回起動でrollbackを再試行する。
- `COMPLETED`でresult/cleanup例外: markerを保持し、次回起動でsuccess resultとcleanupを再試行する。rollbackしてはならない。
- recoverable marker readまたはresult writeの二次失敗: handlerは例外を伝播せず、直前のatomic markerとartifactを保持する。保護対象markerへのwriteは試行しない。
- `PREPARED`/`APPLYING`からの`FAILED` marker write失敗: atomic storeの直前のpre-swap markerを保持し、DBを置換せず二次例外を伝播しない。
- 旧versionが作成したmarkerはstatus enum/schemaを変更しないためmigration不要である。

## Testing Strategy

- `PendingRestoreApplierTest.kt`の現在の`unexpectedException_doesNotEscapeAndWritesFailureResult`を、`DB_SWAPPED`が`FAILED`にならずartifact cleanupも起きない期待へ更新する。
- `ROLLBACK_READY` markerのままlive DB replace後に`DB_SWAPPED` publicationが失敗するfailure pointをfakeで作る。marker/artifact保持だけでなく、同じfixtureの次回起動でDBとDataStoreがrestore前の同一generationへrollbackされ、成功後だけcleanupされることを必須で検証する。
- `DB_SWAPPED`で`reflect()`がthrowするcaseを実行し、marker、DB rollback snapshot、DataStore rollback snapshot、stagingが残ることを検証する。その後同じfake stateで二回目の`runIfNeeded()`を実行し、DBとDataStoreのrollback、failure result確定、cleanupを検証する。
- `MIGRATION_PENDING`ではuser version取得中に例外を注入し、同statusが最後の確定markerであることを確認する。次回起動はcurrent schema versionを返すdeterministic fixtureでvalidation成功から`COMPLETED` finalizationへ進むことを検証する。後続markerが既にatomic確定された後の例外では、その最新markerを保持する。
- `ROLLBACK_REQUIRED`、`COMPLETED`の各startup recovery中へ例外を注入し、markerが`FAILED`へ変わらず次回起動が対応branchを再試行することを検証する。partial rollback failureではさらに次の起動に必要なartifactが全て残ることも検証する。
- `PREPARED`または`APPLYING`のpre-swap例外は`FAILED`にでき、DB replaceが呼ばれないことを回帰確認する。
- recoverable marker read failureとresult write failureを個別注入し、二次例外がescapeせず、確定済みrecovery markerとartifactが保持されることを検証する。marker write failureは`PREPARED`/`APPLYING`から`FAILED`への更新に限定する。
- 全保護対象statusでgeneric exception handlerのmarker write callが0回、cleanup/artifact deletion callが0回であることを検証する。
- 実装後は既存unit test suiteとAndroid buildをCIで実行する。ローカルbuild/testはこのplanning taskでは実行しない。

## Risks / Trade-offs

- [Risk] 同じ想定外例外が毎起動で再発するとrecovery markerとartifactが残り続ける。→ data safetyを優先し、terminalizeせず診断result/logを保持する。既存の手動調査可能性を悪化させない。
- [Risk] `ROLLBACK_READY`はDB置換前後の上方を表し得る。→ 既存recoveryをidempotentに再実行し、状態を推測して`FAILED`へ落とさない。
- [Risk] status分類漏れが将来のenum追加で再発する。→ exhaustive `when`とstatus別unit testで新status追加時にcompile/test reviewを要求する。
- [Risk] broadなrecovery refactorがqueued findingへ accidental scopeを広げる。→ production変更を汎用例外handlerのclassificationへ限定し、既存recovery methodは変更しない。

## Migration Plan

1. handlerのstatus classificationとtest fakeのfailure injectionを追加する。
2. pre-swap、各recoverable/finalization state、二次I/O failure、二回目startupのunit testを追加する。
3. CIでunit testとAndroid buildを確認する。
4. 実装diffが`recordStartupRestoreFailureOnIo()`、その直接helper、対象test/fakeを越える場合は、適用前に本OpenSpecとの差分を条件付きauditし、state/schema/recovery設計の拡張を承認なしで行わない。

rollbackは変更commitのrevertで可能であり、永続schema migrationはない。ただし旧挙動へ戻すとdata inconsistency riskが再発するため、CI failure以外で旧挙動へ戻さない。

## Open Questions

なし。
