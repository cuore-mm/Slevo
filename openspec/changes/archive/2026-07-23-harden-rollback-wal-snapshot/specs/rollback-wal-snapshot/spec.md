## ADDED Requirements

### Requirement: Rollback snapshotはmain DBと必要なWALを完全なfile setとして保存する

システムは既存live DBのrollback snapshotを作成するとき、main DBを必須とし、存在する非空WALを必須fileとして同じsnapshotへ保存しなければならない（SHALL）。再生成可能なSHMをsnapshotの必須fileにしてはならない（MUST NOT）。

#### Scenario: 非空WALを含むsnapshotを作成する

- **WHEN** live main DBと1 byte以上の`-wal`が存在する
- **THEN** システムはmain DBとWALの両方をrollback snapshotへコピーする
- **AND** 両方のcopy完了後だけsnapshotを完成済みとして公開する

#### Scenario: WALが存在しないsnapshotを作成する

- **WHEN** live main DBが存在し、`-wal`が存在しない
- **THEN** システムはmain DBだけをrollback snapshotへコピーする
- **AND** WALを含まない完成snapshotとして公開する

#### Scenario: 空WALを必須fileにしない

- **WHEN** live DBの`-wal`が0 byteである
- **THEN** システムはWALを含まない完成snapshotを作成する

#### Scenario: SHMをsnapshotへ保存しない

- **WHEN** live DBの`-shm`が存在する
- **THEN** システムは`-shm`をrollback snapshotへコピーしない

### Requirement: 必須fileのcopy失敗時はDB swapを開始しない

システムはmain DBまたは非空WALのcopyに失敗したrollback snapshotを公開してはならず（MUST NOT）、live DB swapを開始してはならない（MUST NOT）。

#### Scenario: Main DB copyが失敗する

- **WHEN** rollback snapshotへのmain DB copyが失敗する
- **THEN** システムはbackup failureを記録する
- **AND** snapshot completion metadataを公開しない
- **AND** live main DB、WAL、SHMを変更しない

#### Scenario: 非空WAL copyが失敗する

- **WHEN** live DBに非空WALがあり、そのWAL copyが失敗する
- **THEN** システムはbackup failureを記録する
- **AND** snapshot completion metadataを公開しない
- **AND** DB swapを開始しない
- **AND** live main DBとWALを保持する

### Requirement: Partial snapshotを完成snapshotとして扱わない

システムはsnapshot completion metadataと、そのmetadataが宣言する全必須fileを検証した場合だけrollback snapshotを完成済みと判定しなければならない（SHALL）。main DB fileの存在だけで完成済みと判定してはならない（MUST NOT）。

#### Scenario: Main DBだけがcopyされた状態でprocess deathする

- **WHEN** main DB copy後かつ必須WAL copyまたはcompletion metadata公開前にprocess deathする
- **THEN** 次回起動はそのdirectoryを完成rollback snapshotとして使用しない

#### Scenario: Completion metadataがWALを要求する

- **WHEN** completion metadataがWALを含むsnapshotを宣言する
- **AND** snapshot内にWALが存在しない
- **THEN** システムはsnapshotを不完全として拒否する

#### Scenario: Completion metadataが不正である

- **WHEN** completion metadataがparse不能または未対応versionである
- **THEN** システムはsnapshotをautomatic rollbackへ使用しない

### Requirement: Restore stateはsnapshot作成中とswap開始可能状態を区別する

システムはrollback snapshot作成中の`APPLYING`と、snapshot完成後でswap開始可能な`ROLLBACK_READY`を永続状態として区別しなければならない（SHALL）。新規`APPLYING` markerはrestore開始時にlive DBが存在したかを明示しなければならない（SHALL）。

#### Scenario: Existing DBのsnapshot作成前にAPPLYINGを記録する

- **WHEN** restore開始時にlive DBが存在する
- **THEN** システムは`hadExistingLiveDb=true`の`APPLYING` markerを保存する
- **AND** snapshot完成前に`ROLLBACK_READY`を保存しない

#### Scenario: Snapshot完成後にswapを開始する

- **WHEN** 元live DBの完成rollback snapshotが公開された
- **THEN** システムはmarkerを`ROLLBACK_READY`へ更新する
- **AND** その更新後だけlive DB swapを開始する

#### Scenario: Fresh installのswap準備を記録する

- **WHEN** restore開始時にlive DBが存在しない
- **THEN** システムは`hadExistingLiveDb=false`を保存する
- **AND** rollback snapshotなしで`ROLLBACK_READY`へ進んでからswapを開始する

### Requirement: Process death recoveryは元live DBを保全する

システムは永続state、元live DB有無、snapshot完成状態を組み合わせてrecovery actionを決定しなければならない（SHALL）。状態が曖昧な場合はautomatic rollbackよりdata preservationを優先しなければならない（SHALL）。

#### Scenario: Existing DBのsnapshot作成中にprocess deathする

- **WHEN** `hadExistingLiveDb=true`のstale `APPLYING` markerがある
- **THEN** システムはswap未開始としてlive main DBとWALを保持する
- **AND** partial snapshotをautomatic rollbackへ使用しない
- **AND** restore failureを記録する

#### Scenario: Existing DBのROLLBACK_READY後にprocess deathする

- **WHEN** `hadExistingLiveDb=true`のstale `ROLLBACK_READY` markerがある
- **AND** completion metadataで完成snapshotを検証できる
- **THEN** システムはそのsnapshotからrollbackする

#### Scenario: Fresh installのROLLBACK_READY後にprocess deathする

- **WHEN** `hadExistingLiveDb=false`のstale `ROLLBACK_READY` markerがある
- **THEN** システムはfresh-install restoreで作成されたlive DB sidecarsをcleanupする
- **AND** restore failureを記録する

#### Scenario: 旧markerでswap phaseが不明である

- **WHEN** stale markerに元live DB有無の情報がなく、完成snapshot metadataもない
- **THEN** システムはlive DBまたはrollback filesを上書き・削除しない
- **AND** manual recoveryが必要なfailure detailを記録する

### Requirement: Rollbackはsnapshotが要求するWALを必ず復元する

システムはrollback snapshotのcompletion metadataがWALを含むと宣言する場合、main DBとWALの両方を復元しなければならない（SHALL）。必須WALを復元できないrollbackを成功として扱ってはならない（MUST NOT）。

#### Scenario: Main DBとWALのrollbackに成功する

- **WHEN** completion metadataがWALを含む完成snapshotを宣言する
- **AND** main DBとWALのrestoreが成功する
- **THEN** システムはrollback成功を返す
- **AND** SQLiteが再生成するSHMをsnapshotから復元しない

#### Scenario: 必須WALのrestoreに失敗する

- **WHEN** main DB restore後に必須WALのrestoreが失敗する
- **THEN** システムはrollback失敗を返す
- **AND** rollback snapshotを削除しない
- **AND** manual recovery用failure detailを記録する

#### Scenario: WALを含まないsnapshotをrollbackする

- **WHEN** completion metadataがWALを含まない完成snapshotを宣言する
- **THEN** システムはmain DBだけを復元してrollback成功を返す
