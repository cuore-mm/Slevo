## Context

`BackupReader.readBackup` は `ZipInputStream` で最大5個の既知file entryと2個の既知directory entryを処理する。現在、`database/slevo.db` は `zip.copyTo(output)` で一時ファイルへstreamingし、JSON entryは `zip.readBytes()` でheapへ読み込むが、どちらにも展開後サイズ上限がない。

ZIP headerの`ZipEntry.size`は入力ファイル内の申告値であり、未知または改ざんされた値になり得る。したがって、小さい圧縮入力から大量のzero byte等を生成するZIP bombに対し、headerだけで安全性を保証できない。

現在の流れは次のとおりである。

```text
SAF InputStream
  -> ZipInputStream
     -> database/slevo.db -> unbounded copyTo -> temp DB file
     -> JSON entries      -> unbounded readBytes -> heap
  -> manifest/JSON/DB validation
  -> BackupPreview
```

サイズ超過はvalidationより前にprocess crashやストレージ枯渇を起こせるため、entryを展開している最中に停止する必要がある。また、アプリが生成したバックアップが同じresource policyで拒否されないよう、export側も同じpolicyを使用する。

## Goals / Non-Goals

**Goals:**

- 実際に展開したbyte数をentry別・合計で制限する。
- DBはheapへ読み込まずbounded streamingを維持する。
- JSONは小さい上限内でのみmemoryへ保持する。
- 上限超過、I/O失敗、malformed/validation失敗、cancellation後に一時DBを確実に削除する。
- アプリ自身がresource limit超過バックアップを成功としてexportしない。
- 小さいlimitをtestから注入し、巨大なtest fixtureを生成せず境界値を検証する。

**Non-Goals:**

- 圧縮率そのものを制限すること。
- `ZipEntry.size`だけをsecurity boundaryとして使用すること。
- Moshi JSON parseを全面的にstreaming APIへ変更すること。
- 利用可能ストレージ容量を事前予約または保証すること。
- 暗号化、署名、パスワード保護を追加すること。
- ZIP entry名、manifest schema、Room/DataStore schema、UI文言を変更すること。

## Decisions

### 1. 共有resource policyを導入する

`app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupResourceLimits.kt` に、entry別上限、合計上限、entry数上限を表すimmutableな`BackupResourceLimits`を配置する。production defaultは次の値とする。

| 対象 | 展開後上限 |
|---|---:|
| `manifest.json` | 64 KiB |
| `database/slevo.db` | 256 MiB |
| `datastore/settings.json` | 1 MiB |
| `datastore/tabs.json` | 64 KiB |
| `datastore/cookies.json` | 8 MiB |
| 全entry合計 | 272 MiB |
| ZIP entry数（directoryを含む） | 7 |

各値は`Long`で保持し、`limitForEntry(name)`で既知file entryの上限を返す。`BackupReader`とexport writerは同じdefault instanceを使用する。constructorにはdefault引数を設定し、testは数byteのcustom policyを渡す。

256 MiB DBは現行schemaの一般的な1–50 MiB程度のDBへ十分な余裕を持たせつつ、無制限展開を防ぐ。合計272 MiBはDBとすべてのJSON上限の合計を上回る。将来正常利用で不足が判明した場合は、backup formatを変えずpolicy値だけを別changeで引き上げられる。

### 2. 実測byte数を唯一の最終判定にする

`BackupReader.kt` に、`InputStream`から固定bufferで読み、次の書き込み前にentry counterとtotal counterを確認するbounded helperを追加する。上限を1 byteでも超える場合は`BackupResourceLimitExceededException`を投げる。

- DB entry: temp fileの`OutputStream`へbounded copyする。
- JSON entry: `ByteArrayOutputStream`へbounded copyし、完了後に`toByteArray()`する。
- directory entry:展開byteは0としてentry数だけ計上する。

`ZipEntry.size`が既知かつ上限超過なら早期拒否に利用してよいが、申告値が上限内・`-1`・不正でも実測counterを省略してはならない。

buffer読取後に上限を確認する実装では最大buffer分を余計に読み得るため、各loopは`remaining + 1`以下だけを要求し、上限超過検出用の1 byteを超えてmemory/fileへ書き込まない。DB temp fileは上限以下のbyteだけを保持する。

### 3. 上限超過を専用例外から`Invalid`へ変換する

`BackupResourceLimitExceededException`はentry名、entry実測値または検出値、entry上限、total値、total上限を診断情報として保持する。`BackupReader.readBackup`はgeneric stream errorより先にこの例外をcatchし、temp DBを削除して`BackupRestoreResult.Invalid`を返す。

詳細例:

```text
entry too large: database/slevo.db exceeds 268435456 bytes
total decompressed size exceeds 285212672 bytes
too many ZIP entries: limit=7
```

詳細はlog/diagnostic用であり、ViewModelの既存generic invalid-backup表示を変更しない。`OutOfMemoryError`をcatchする方式は採用せず、allocation前にbounded helperで防止する。

一時DBのcleanupはlimit例外だけに限定しない。temp file生成後は`dbTempFile`のownershipを`BackupReader`が持ち、成功した`BackupPreview`へ明示的にownership transferした場合だけ削除対象から外す。limit超過、ZIP read/write `IOException`、malformed ZIP、manifest/JSON/DB validation失敗、coroutine cancellationを含むすべての不成功経路で`finally`または同等の単一cleanup境界から部分fileを削除する。既存APIがcancellationを上位へrethrowする場合も、cleanup完了後にrethrowする。

### 4. entry数も明示的に制限する

`ZipInputStream.nextEntry`でentryを取得するたび、file/directoryを問わずcounterを増やし、7件を超えた時点で拒否する。既存のunknown pathとduplicate file rejectionは維持する。entry数limitは繰り返しdirectoryや将来のvalidation漏れに対する独立防御となる。

### 5. export側も同じpolicyを超える出力を成功扱いしない

`BackupZipWriter`へ`BackupResourceLimits`をdefault引数で渡し、entry追加時に次を検証する。

- `writeEntry` / `writeJsonEntry`: 生成済みUTF-8 byte配列のsizeがentry上限以下である。
- `writeFileEntry`: `File.length()`は明らかな超過を早期拒否する補助に限定し、実際にsource streamから読み取ってZIPへ書くbyte数をbounded helperで計測する。
- 全entry: 書き込み予定のuncompressed byte数を合計し、total上限以下である。
- entry count: `putNextEntry`直前にfile/directoryを含むすべてのemit対象を数え、policy以下である。

byte配列と事前に確定したfile lengthが上限超過ならentryを書き始める前に拒否する。file streamの実測値が事前値より増えた場合は、上限を超えるbyteをZIPへ書かず`BackupResourceLimitExceededException`を投げ、既存`BackupRepositoryImpl.writeZip`のfailure pathで`BackupExportResult.Failure`へ変換する。これによりsource fileの変更有無に依存せず、成功したexportの実展開量がrestore policy内になる。

entry countは`ZipOutputStream.putNextEntry`を呼ぶ直前に、file/directoryを区別せずすべてのemit対象について加算する。現行writerが明示的directory entryを出力しない場合も、将来追加されたdirectoryを同じcounterへ通す。restore側もfile/directoryを含む全entryを同じ7件上限で数える。

export全体をSAF URI open前にpreflightする案は、Moshi serializationの重複とrepository責務拡大を招くため採用しない。上限超過時に選択済み出力先が空または不完全になる可能性は既存のwrite failure契約と同じであり、成功扱いしない。

### 6. free-spaceは補助情報としsecurity boundaryにしない

`File.usableSpace`は他processの利用やprovider実装により書き込み完了を保証しないため、事前判定は追加しない。temp DB writeの`IOException`は既存stream failureとして扱い、bounded limitによりアプリが意図して展開する最大量だけを制限する。

## Error Cases / Compatibility

- entry上限ちょうど: 許可する。
- entry上限+1 byte: 追加byteを書き込まず`Invalid`として停止する。
- 各entryは上限内だが合計上限超過: `Invalid`として停止する。
- header sizeは上限内だが実展開量が超過: 実測counterで拒否する。
- entry数が8件以上: 内容を展開する前に拒否する。
- DB size超過後: 部分temp fileを削除する。
- JSON size超過後: oversized byte配列をmapへ保存しない。
- export sourceの実stream byte数がpolicy超過: 上限を超えるbyteをZIPへ書かずbackup作成を失敗扱いにし、成功Snackbarを表示しない。
- 正常backup:既存parse、DB validation、preview/restore動作を維持する。

## Risks / Trade-offs

- [Risk] 256 MiBを超える正当な既存DBを持つユーザーがexport/restoreできなくなる。 → 同じpolicyをexportとrestoreへ適用して不整合を防ぎ、limit超過を明示的失敗として記録する。実利用で必要ならpolicyを別changeで引き上げる。
- [Risk] JSON上限が将来model拡張に不足する。 → entryごとに独立定数を持ち、schema変更時にsize policy reviewをtaskへ含める。
- [Risk] total counterの加算overflowでlimitを回避される。 → `Long`加算前に`current > limit - increment`形式で比較し、overflowしない。
- [Risk] dedicated exceptionがDB copy内のgeneric wrapで失われる。 → DB temp cleanup後に同じ例外instanceをrethrowし、outer catchで`Invalid`へ変換するtestを追加する。
- [Risk] storage write failureやcancellationがlimit専用catchを通らずpartial temp DBを残す。 → temp DB ownershipを単一`try/finally`境界で管理し、success transfer以外は原因に関係なく削除する。
- [Risk] restoreとexportのentry名/limit mappingがずれる。 → shared policyを唯一のmapping sourceとし、全既知entryにlimitがあるtestを追加する。

## Migration Plan

1. shared resource policyと専用例外を追加する。
2. `BackupReader`のDB/JSON readとentry countをbounded化する。
3. `BackupZipWriter`へ同じpolicyを適用する。
4. 境界値、合計、高圧縮、cleanup、export/restore整合性testsを追加する。
5. Room/DataStore migrationは不要である。
6. 問題発生時は本changeをrevertする。すでに拒否された入力や失敗したexportに永続データmigrationはない。

## Implementation Contract

- production defaultはmanifest 64 KiB、DB 256 MiB、settings 1 MiB、tabs 64 KiB、cookies 8 MiB、total 272 MiB、entry count 7とする。
- size計算は`Long`を使用し、overflow安全な比較を行う。
- restore判定は`ZipEntry.size`ではなく実際に展開したbyte数を必ず使用する。
- DB entryを`ByteArray`化してはならない。固定bufferによるbounded streamingを維持する。
- JSON entryに無制限の`readBytes()`を使用してはならない。
- limit超過検出用の1 byteを超えてtemp fileまたはheap bufferへ書き込んではならない。
- size/entry count超過は`BackupRestoreResult.Invalid`とし、temp DBを削除する。その他の不成功経路も既存result/cancellation semanticsを維持しつつtemp DBを削除する。
- temp DB ownershipは成功previewへのtransfer時だけreaderから外し、それ以外は単一cleanup境界で削除する。
- `writeFileEntry`は`File.length()`に加えて実際のsource stream byte数を計測し、上限を超えるbyteをZIPへ書かない。
- export/restoreのentry countはfile/directoryを含む全ZIP entryを数える。
- exportとrestoreは同じ`BackupResourceLimits` defaultを使用する。
- export limit超過は`BackupExportResult.Failure`とし、成功扱いしない。
- existing path validation、duplicate rejection、manifest/JSON/DB validation、cleanup ownershipを変更しない。
- 新規class/interface/exceptionにはannotationより前にKDocを追加し、非自明なbounded copy/helperにはKDocを追加する。
- UI文言、ZIP entry名、manifest、Room/DataStore、restore state machine、dependencyを変更しない。

## Testing Strategy

- `BackupReaderTest`は小さいcustom policyを注入し、巨大fixtureを作らない。
- DB/JSONについて上限ちょうどを許可し、上限+1を拒否する。
- 圧縮後は小さいが展開後に超過するzero-filled entryを拒否する。
- 個別上限内の複数entryでtotal上限を超えた場合を拒否する。
- total上限ちょうどを許可し、上限+1 byteを拒否する。
- file/directoryを含むentry数7件ちょうどを許可し、8件目を内容展開前に拒否する。
- DB超過後にcontrolled temp fileが削除される。
- temp file write `IOException`、malformed ZIP、validation failure、cancellation後にもcontrolled temp fileが削除される。
- limit exceptionがgeneric errorへwrapされず`Invalid`になる。
- `BackupZipWriterTest`は各entryとtotalのexport上限超過を成功扱いせず、source streamの実byte数が事前file sizeより大きい場合も実測値で拒否する。
- file/directoryを含むentry countをexport/restoreで同じように数える。
- 小さいcustom policyでexportした境界内ZIPを同じpolicyの`BackupReader`が受理するinteroperability testを追加する。
- shared policyが全既知file entryへlimitを返すことを検証する。
- 既存`BackupReaderTest`、`BackupZipWriterTest`、repository関連testsを回帰実行する。
- Android CIでunit testsとCI APK buildを実行する。

## Implementation Status

2026-07-13時点で、shared resource policy、restoreのDB/JSON bounded read、entry/total/count制限、export側の同policy適用、DB output stream close、ZIP read中のcancellation cleanupは実装済みである。commit `86a62011` に対するAndroid CI Run #29220274306は成功している。

ただし、CI成功だけでは以下のspec契約を十分に証明していないため、本changeは未完了として扱う。

- temp DB ownershipがread開始からsuccess transferまで単一の`try/finally`境界になっておらず、DB validator等の後続処理が`CancellationException`を投げる経路を専用testで固定していない。
- bounded helperは超過byteをoutputへ書かないが、designで定めた`remaining + 1`以下のread requestにはなっていない。
- `IOException` cleanupを名乗る既存testは成功ownership transferを検証しており、実際のwrite failureを注入していない。
- 高圧縮zero-filled ZIP、manifest/settings/tabs/cookies各entry、valid backupのtotal上限ちょうど、source file実stream増加を検証していない。
- repositoryのresource limit例外から`BackupExportResult.Failure`へのmappingを直接検証していない。
- export→restore interoperability testはinvalid JSONを使用しており、同policyで正常backupをround-tripできることを証明していない。

残件は`tasks.md`の「実装後監査フォローアップ」に集約する。既存coreをrollbackせずfix-forwardし、各残件と最終CIが成功してからchange完了とする。

## Open Questions

なし。production limit変更やprovider側のstorage reservationが必要になった場合は別changeで扱う。
