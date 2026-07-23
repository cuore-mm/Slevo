## ADDED Requirements

### Requirement: Writer ownershipはcancellation後も必ず解放される

システムは`withWritePermit`がwriter waiterをgateへ登録した後、QUEUED、RESERVED、RUNNINGのどの状態でcancelまたは例外終了しても、そのwaiterをqueueから除去するかactive writer予約を1回だけ解放しなければならない（SHALL）。cleanupのためのlock待ちはcallerのcancellationで中断されてはならない（MUST NOT）。

#### Scenario: Queued writerをlock競合中にcancelする

- **WHEN** writerがqueueでsignal待機中にcancelされ、cleanup用state lockが他coroutineに保持されている
- **THEN** writerはlock解放後にqueueから除去される
- **AND** 元の`CancellationException`が呼び出し元へ伝播する
- **AND** 後続writerとsuspensionが進行できる

#### Scenario: Running writerをlock競合中にcancelする

- **WHEN** writer blockがcancelされ、release用state lockが他coroutineに保持されている
- **THEN** システムはlock解放後に`activeWriters`予約を1回だけ解放する
- **AND** 元の`CancellationException`を伝播する
- **AND** 後続suspensionが開始できる

#### Scenario: Writer blockが通常例外を投げる

- **WHEN** writer blockが通常exceptionを投げる
- **THEN** システムはwriter予約を解放する
- **AND** 同じexceptionを呼び出し元へ伝播する

### Requirement: Reserved writer transitionは単一cleanup境界で保護される

システムはqueued writerのsignal受信後から`RESERVED -> RUNNING`遷移およびuser block開始までを、cancellation時に必ずreservation cleanupへ到達するownership境界で管理しなければならない（SHALL）。cancel済みwriterは新しいuser blockを開始してはならない（MUST NOT）。

#### Scenario: Signal受信後かつRUNNING遷移前にcancelする

- **WHEN** writerが`RESERVED`としてactive writer数へ計上されsignalを受信した後、`RUNNING`遷移前にcancelされる
- **THEN** システムはreserved tokenを1回だけ解放する
- **AND** writer user blockを実行しない
- **AND** 後続suspensionが進行できる

#### Scenario: RUNNING遷移直後にcancelを観測する

- **WHEN** writerが`RUNNING`へ遷移した時点でcaller jobがcancel済みである
- **THEN** システムはuser block開始前にcancellationを観測する
- **AND** ownership cleanupでactive writer tokenを解放する

### Requirement: Suspension ownershipはcancellation後も必ず解放される

システムは`withWritesSuspended`がsuspension waiterをgateへ登録した後、QUEUEDまたはACTIVEのどちらでcancelまたは例外終了しても、そのwaiterをqueueから除去するか`suspensionActive`を解除しなければならない（SHALL）。cleanupのためのlock待ちはcallerのcancellationで中断されてはならない（MUST NOT）。

#### Scenario: Queued suspensionをlock競合中にcancelする

- **WHEN** suspensionがqueueでsignal待機中にcancelされ、cleanup用state lockが他coroutineに保持されている
- **THEN** suspensionはlock解放後にqueueから除去される
- **AND** 元の`CancellationException`が伝播する
- **AND** FIFO上の後続waiterが進行できる

#### Scenario: Active suspensionをlock競合中にcancelする

- **WHEN** suspension blockがcancelされ、release用state lockが他coroutineに保持されている
- **THEN** システムはlock解放後に`suspensionActive=false`へ戻す
- **AND** 元の`CancellationException`を伝播する
- **AND** 後続writerが開始できる

#### Scenario: Suspension blockが通常例外を投げる

- **WHEN** suspension blockが通常exceptionを投げる
- **THEN** システムはactive suspensionを解放する
- **AND** 同じexceptionを呼び出し元へ伝播する

### Requirement: NonCancellable範囲はgate state mutationだけに限定される

システムはcancellationから保護する範囲を、gate state lock取得と同期的state mutationに限定しなければならない（SHALL）。user block、signal待機、DB I/O、外部callbackを非キャンセル範囲で実行してはならない（MUST NOT）。

#### Scenario: User blockがcancelされる

- **WHEN** writerまたはsuspensionのuser blockがcancelされる
- **THEN** システムはuser blockを継続完了させずcancellationを伝播する
- **AND** gate state cleanupだけを非キャンセルで完了する

#### Scenario: Cleanup lockが競合する

- **WHEN** cancel済みcoroutineのcleanupがstate lock取得を待つ
- **THEN** システムはlock取得後に短い同期state mutationを完了する
- **AND** cleanup完了後に元のcancellation semanticsへ戻る

### Requirement: Cancellation後もgateは再利用可能である

システムはwriterまたはsuspensionのcancellation後にFIFO、writer batching、suspension exclusivityを維持し、fresh writerとfresh suspensionを受け入れなければならない（SHALL）。

#### Scenario: 複数writer cancel後にsuspensionを開始する

- **WHEN** queued、reserved、running writerがcancel cleanupを完了する
- **THEN** 後続`withWritesSuspended`はtimeoutせず開始・終了する

#### Scenario: Suspension cancel後にwriterを開始する

- **WHEN** queuedまたはactive suspensionがcancel cleanupを完了する
- **THEN** 後続`withWritePermit`はtimeoutせず開始・終了する

#### Scenario: Cancelled waiterをFIFO queueから除去する

- **WHEN** FIFO queue中間のwriterまたはsuspensionがcancelされる
- **THEN** 残るwaiterは元の相対順序で進行する
