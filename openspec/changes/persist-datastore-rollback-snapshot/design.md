## Context

`PendingRestoreApplier.applyRestore()`は`PREPARED → APPLYING → ROLLBACK_READY → DB_SWAPPED → MIGRATION_PENDING`の順で進む。DB置換後、`RealPendingRestoreDataStoreReflector.reflect()`がsettings、tabs、cookiesを順番に更新し、通常のwrite exceptionでは`PendingRestoreDataStoreWriter.DataStoreSnapshot`からbest-effort rollbackする。

現在の`DataStoreSnapshot`はprocess memoryにしか存在しない。`DB_SWAPPED` marker確定後から`MIGRATION_PENDING` marker確定前までにprocessが終了すると、次回起動の`recoverFromStaleApplyingOrDbSwapped()`はDB rollbackだけを行う。settingsだけ反映、settings/tabsだけ反映などの状態が残り、DBとDataStoreが異なるrestore generationになる。

問題はstale `DB_SWAPPED`だけに限定されない。DataStore反映後もDB rollback snapshotはmigration完了確認まで保持され、stale `MIGRATION_PENDING`のvalidation failureや`ROLLBACK_REQUIRED`でもDB rollbackが起きる。このため、DBをrestore前へ戻す全経路で同じDataStore rollback sourceを使用する必要がある。

対象DataStoreは`SlevoPreferenceDataStores`のsettings、tabs、cookiesである。PreferencesはString、Boolean、Int、Long、Float、Double、StringSetを保持でき、keyが存在しない状態と空値を区別する必要がある。`.preferences_pb`の直接copy/rename/deleteはactive DataStore instanceのlockingと内部formatを迂回するため使用しない。

## Goals / Non-Goals

**Goals:**

- DB置換前にrestore前DataStore値をprocess death後も読めるrollback snapshotとして確定する。
- settings、tabs、restore対象cookiesの全key、型、値、key不在をlosslessに保持する。
- `ROLLBACK_READY`以降のDB rollbackでDataStoreも同じrestore前generationへ戻す。
- DBまたはDataStore rollbackが未完了の場合、artifactを保持して次回起動で再試行する。
- fresh-install branchでも既存DataStoreをsnapshot/rollbackする。
- migration完了またはrollback完了までsnapshotを保持し、その後pending cleanupで削除する。

**Non-Goals:**

- 複数DataStoreを1つのACID transactionとして更新する汎用frameworkの導入。
- DataStore `.preferences_pb` fileの直接copy、rename、delete。
- backup archive内のsettings/tabs/cookies JSON schema変更。
- Room schema、pending marker JSON field、result JSON field、restore status enumの追加。
- startup restore結果をUIへ通知する別指摘の対応。

## Decisions

### 1. 3 DataStoreを1つの型付きrollback snapshot JSONへ保存する

`filesDir/pending-restore/datastore-rollback.json`相当の単一fileへ、settings、tabs、cookiesのsnapshotをまとめる。単一fileにすることで、3 file間のpartial publishを避け、`AtomicFile`による1回のcommitでsnapshot全体を確定できる。

新しいsnapshot modelは次の情報を持つ。

- `formatVersion`: snapshot decoderの互換性判定用。初期値は1。
- `settings`: settings Preferencesのentry list。
- `tabs`: tabs Preferencesのentry list。
- `cookies`: cookies restore対象外なら`null`、対象で空ならempty list、値があればentry list。
- 各entry: preference key名、value type、対応するtyped value。

value typeはPreferencesが取り得るString、Boolean、Int、Long、Float、Double、StringSetを全て扱う。各entryはtypeに対応するvalueをちょうど1つ持たなければならない。keyはstore内で一意、serialization時はkey昇順、StringSetは文字列昇順とし、deterministic JSONにする。

known application keyだけをdomain DTOへ変換する案は、将来追加されたkeyや「未設定」とdefault値の差を失うため採用しない。raw `.preferences_pb` copyはDataStore APIを迂回するため採用しない。

### 2. Snapshot fileは専用storeでatomicにpublishする

pending packageへsnapshot modelのencode/decode、Preferences変換、atomic file I/Oを担当する専用collaboratorを追加する。具体的なclass/file名は実装前調査でpackage命名に合わせるが、次のcontractを持つ。

- Preferencesからsnapshot modelへlosslessに変換する。
- snapshot modelを`AtomicFile.startWrite()/finishWrite()/failWrite()`でpublishする。
- read時にformat version、storeごとのkey重複、value type、typed value個数を検証する。
- missing、partial、malformed、unsupported snapshotを成功扱いしない。
- snapshotをPreferencesへ戻す際は対象storeを`clear()`してから全entryを書き戻す。

`AtomicPendingRestoreMarkerFile`はmarker model専用のため無理に汎用化しない。必要ならstream protocolだけを重複させ、markerの既存挙動を変更しない。

snapshotにはcookies/auth情報が含まれ得るが、staged cookiesと同じapp-private `filesDir/pending-restore`に置き、external storageへ出さない。pending cleanup以外のlog/resultへsnapshot内容を含めない。

### 3. DataStore target validationとsnapshot確定をDB置換前に行う

`PendingRestoreDataStoreReflector`の責務を、少なくとも次のphaseへ分ける。

1. `prepare`: staged settings/tabs/cookies JSONを全てparseし、cookiesを含むwrite targetをpre-validationする。現在のsettings/tabs/対象cookies Preferencesを取得し、durable snapshotをatomicに確定する。
2. `reflect`: staged JSONを再読込・再検証し、settings、tabs、cookiesを反映する。write exception時はdurable snapshotから即時rollbackを試す。
3. `rollback`: durable snapshotを検証してsettings、tabs、対象cookiesをrestore前状態へ戻す。

method名は既存`reflect()`との整合を確認して決定する。fakeはprepare/reflect/rollback call、結果、順序を記録できるようにする。

`applyRestore()`の順序は次とする。

1. `APPLYING` markerで`hadExistingLiveDb`を確定。
2. live DBがある場合、DB rollback snapshotを完成させる。
3. staged DataStore targetを全parse/pre-validationし、現在DataStoreのdurable snapshotを完成させる。
4. DB/DataStoreの必要なrollback sourceが揃った後だけ`ROLLBACK_READY` markerを確定。
5. DB replaceとpost-replace validationを行う。
6. `DB_SWAPPED` markerを確定。
7. DataStoreを反映する。
8. `MIGRATION_PENDING` markerを確定し、DB/DataStore rollback sourceを保持する。

DataStore prepareまたはsnapshot publishが失敗した場合はDBを置換せず、DataStoreも変更しない。作成済みDB rollback snapshotとpending stagingはfailure result記録後にcleanupできる。

DB snapshotの前にDataStore snapshotを取る案も安全だが、DB snapshot作成失敗時に不要なcookieを含むsnapshotを書き出す。DB snapshot完成後、DB置換前にDataStore snapshotを取ることで不要な永続化を減らしつつ安全性を維持する。

### 4. DB rollbackとDataStore rollbackを単一のsuspend orchestrationにする

現在の`rollbackAndFail()`をsuspend可能なrollback orchestrationへ変更する。rollback対象状態では次を順に行う。

1. `hadExistingLiveDb == true`ならcomplete DB snapshotからlive DBをrestoreする。
2. `hadExistingLiveDb == false`ならrestore中に作られたlive DB setをcleanupする。
3. durable DataStore snapshotを読み、settings、tabs、cookiesをrestoreする。
4. DBとDataStoreの両方が成功した場合だけFAILED resultを確定し、pending directoryをcleanupする。
5. どちらかが失敗した場合は`ROLLBACK_REQUIRED` marker、result、DB/DataStore snapshot、stagingを保持し、次回起動で両方を再試行する。

DB rollbackが先に成功してDataStore rollbackが失敗しても、startup restoreは通常app初期化より前に実行される。artifactを保持し、次回起動でDB restoreをidempotently再実行した後にDataStore restoreを再試行する。

`recoverFromRollbackRequired()`はDB snapshotの有無だけで即quarantineせず、`hadExistingLiveDb`とDataStore snapshotを確認する。fresh-installではDB snapshotがないことが正常で、live DB cleanupとDataStore rollbackを実行する。

### 5. DataStore rollback対象をstatusとsnapshot availabilityで決定する

新しいflowで`ROLLBACK_READY`はDataStore write前なので、stale `ROLLBACK_READY` recoveryではDataStore値は通常未変更である。ただし同じrollback helperを使ってsnapshotを書き戻してもrestore前値へのidempotent操作となるため、DB/DataStoreの両方をrestoreする。

次の経路はdurable DataStore rollbackを必須とする。

- stale `DB_SWAPPED`
- DataStore reflect failure
- stale `MIGRATION_PENDING`のpre/post migration validation failure
- unexpected intermediate DB version
- completion checkerが設定した`ROLLBACK_REQUIRED`
- fresh-installのDB cleanupを伴うrollback

`COMPLETED`はDB/DataStore restore成功済みとしてsuccess resultとpending cleanupを再試行する。snapshotは`COMPLETED` cleanupまで保持する。

### 6. Snapshotがないlegacy pending状態は破壊的なDB-only rollbackを避ける

更新前versionが`DB_SWAPPED`または`MIGRATION_PENDING` markerを残した場合、durable DataStore snapshotが存在しない可能性がある。この状態でDBだけをrollbackすると、今回の不整合を再発させる。

legacy markerでsnapshotがない場合は、完全rollbackを成功扱いしない。live DB、rollback DB、staged DataStore JSONを保持し、failure resultにdurable DataStore snapshot欠損とmanual recoveryが必要であることを記録する。自動cleanupと新規restore準備を行わない。

forward completionも検討したが、process death前にどのDataStore writeが成功したか分からず、再write failure時にrestore前値へ戻せない。artifact preservationを優先する。

## Implementation Contract

1. `PendingRestoreDataStoreWriter.kt`でPreferencesの全supported value typeをsnapshot entryへ変換し、逆変換できるhelperを追加する。
2. snapshot conversionはkey不在、empty StringSet、cookies対象外を区別し、unchecked castは既存`restoreToMutablePreferences()`と同様に限定されたhelperへ閉じ込める。
3. snapshot fileは単一JSONを`AtomicFile`でpublishし、write streamを`finishWrite()/failWrite()`前にcloseしない。
4. snapshotのformatVersion、unique key、type/value整合性をread後かつDataStore edit前に検証する。
5. `PendingRestoreDataStoreReflector`へprepare/rollback責務を追加し、全production/fake implementationを更新する。
6. `applyRestore()`はDB rollback snapshotとDataStore snapshotが両方完成するまで`ROLLBACK_READY`を書かず、DB replaceを開始しない。
7. `rollbackAndFail()`およびそれを呼ぶrecovery methodsを必要に応じて`suspend`化し、DBとDataStoreのrollback結果を個別に記録する。
8. rollback未完了時は`ROLLBACK_REQUIRED`とartifactを保持し、`cleanupPending()`を呼ばない。両方完了時だけFAILED resultとcleanupを行う。
9. `hadExistingLiveDb == false`でもDataStore snapshot/rollbackを省略しない。
10. marker/result data class、restore status enum、backup archive model、`.preferences_pb` fileを変更しない。
11. 新規typeとnon-trivial functionにはannotationより上のKDocを追加し、約30行を超えるfunctionはsection commentsで分割する。

## Error Cases and Compatibility

- staged JSON parse/cookie pre-validation failure: snapshotまたはDB replace前に失敗し、DataStoreを変更しない。
- snapshot read/write failure: DB replaceを開始せず、failure result後に安全なpending cleanupを行う。
- snapshot type/value不整合: DataStoreをclear/editせずrollback未完了としてartifactを保持する。
- process death during snapshot write: markerはAPPLYINGのままでDB/DataStore未変更。未確定snapshotを使用しない。
- process death during DataStore writes: markerはDB_SWAPPEDのまま。次回起動でDBとDataStoreをsnapshotからrollbackする。
- process death afterDataStore writes beforeMIGRATION_PENDING: 同じDB_SWAPPED recoveryで両方rollbackする。
- migration validation failure: DBとDataStoreを両方rollbackする。
- DataStore rollback failure: ROLLBACK_REQUIREDとsnapshotを保持し、次回起動で再試行する。
- legacy pendingにsnapshotがない: DB-only rollback/cleanupを行わずartifactを保全する。

## Testing Strategy

- snapshot model: supported全type、empty/missing、StringSet ordering、duplicate key、unknown format/typeをheadless unit testで検証する。
- atomic snapshot store: normal publish、更新中断、partial/malformed JSON、parent failure、cleanupをRobolectric/temporary filesystemで検証する。
- DataStore writer: settings/tabs/cookiesのfull overwrite、absent key削除、empty cookies、cookies対象外を検証する。
- applier fake: DB snapshot → DataStore snapshot → ROLLBACK_READY → DB replace → DB_SWAPPED → reflectの順序を検証する。
- process death: DB_SWAPPED直後、settings後、tabs後、cookies後、MIGRATION_PENDING前の各状態からcold-start recoveryを実行し、DB/DataStoreがrestore前値へ戻ることを検証する。
- migration/fresh install: MIGRATION_PENDING validation failure、ROLLBACK_REQUIRED、hadExistingLiveDb=falseを検証する。
- rollback failure: DataStore restore failure後にartifactが残り、次回起動のretry成功後にcleanupされることを検証する。

## Risks / Trade-offs

- [Risk] cookiesを含むsnapshotでpending storageとserialization memoryが増える。→ app-private領域にrestore lifecycle中だけ保持し、単一snapshotを1回だけ作成してCOMPLETED/rollback完了後にcleanupする。
- [Risk] generic Preferences型変換で型lossやunchecked castが起きる。→ supported typeを明示列挙し、unknown typeをrejectし、全type round-trip testsを追加する。
- [Risk] rollback helperのsuspend化が多くのrecovery pathへ波及する。→ `runIfNeededOnIo()`配下だけに変更を閉じ、call graphをtaskで列挙してcompile/testする。
- [Risk] DB rollback成功後にDataStore rollbackが失敗すると一時的に不整合になる。→ app初期化前に実行し、artifactとROLLBACK_REQUIREDを保持して次回起動で再試行する。
- [Risk] legacy snapshot欠損状態を自動解決できない。→ DB-only rollbackで悪化させず、全artifactと診断情報を保持する。

## Migration Plan

1. snapshot model/converterとvalidation testsを追加する。
2. atomic snapshot storeとfilesystem testsを追加する。
3. reflectorへprepare/rollback contractを追加し、writer/fakeを更新する。
4. apply flowのsnapshot orderingとROLLBACK_READY invariantを更新する。
5. 全DB rollback pathをcombined rollbackへ移行する。
6. process-death、migration、fresh-install、rollback retry testsを追加する。
7. Android CIで全unit testsとAPK buildを検証する。

backup/pending marker schema migrationは不要である。新versionが作成するsnapshotはformatVersion 1とし、unsupported future versionは自動rollbackせずartifactを保持する。

## Open Questions

なし。snapshot filenameと新規class名は既存pending packageの命名規則に合わせて実装時に確定する。
