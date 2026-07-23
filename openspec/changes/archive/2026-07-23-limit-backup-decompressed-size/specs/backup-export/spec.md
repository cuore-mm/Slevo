## ADDED Requirements

### Requirement: 復元resource policyと整合するバックアップ出力
システムは同じversionの復元resource policyを超えるバックアップを作成成功として扱ってはならない（MUST NOT）。exportとrestoreはmanifest 64 KiB、DB 256 MiB、settings 1 MiB、tabs 64 KiB、cookies 8 MiB、合計272 MiB、file/directoryを含むentry数7件のdefault policyを使用しなければならない（MUST）。1 KiBは1024 bytes、1 MiBは1024×1024 bytesとし、各size上限はinclusiveとする。

#### Scenario: DB source fileが復元上限を超える
- **WHEN** export対象DB fileのsizeがDB entryの復元上限を超える
- **THEN** システムはバックアップ作成を失敗扱いにし、成功表示を行わない

#### Scenario: DB sourceの実stream sizeが事前sizeを超える
- **GIVEN** export開始前のDB file sizeはDB entry上限以下である
- **WHEN** ZIPへのstreaming中に実際に読み取ったbyte数がDB entry上限を超える
- **THEN** システムは上限を超えるbyteをZIPへ書かず、バックアップ作成を失敗扱いにする

#### Scenario: JSON entryが復元上限を超える
- **WHEN** UTF-8へ変換したJSON entryのsizeが対応する復元上限を超える
- **THEN** システムはバックアップ作成を失敗扱いにし、成功表示を行わない

#### Scenario: export entryの合計sizeが復元上限を超える
- **GIVEN** 各export entryは個別上限以下である
- **WHEN** 全entryのuncompressed size合計が復元合計上限を超える
- **THEN** システムはバックアップ作成を失敗扱いにし、成功表示を行わない

#### Scenario: export entryの合計sizeが復元上限ちょうどである
- **WHEN** 全entryのuncompressed size合計が272 MiBまたは注入されたpolicyの合計上限と等しい
- **THEN** システムは合計sizeを理由にバックアップ作成を失敗扱いにしない

#### Scenario: export entry数が上限を超える
- **WHEN** fileとdirectoryを含むemit予定entry数が7件を超える
- **THEN** システムは8件目をZIPへ追加せず、バックアップ作成を失敗扱いにし、成功表示を行わない

#### Scenario: export entry数が上限ちょうどである
- **WHEN** fileとdirectoryを含むemit予定entry数が7件または注入されたpolicyの上限と等しい
- **THEN** システムはentry数を理由にバックアップ作成を失敗扱いにしない

#### Scenario: resource policy内のバックアップを作成する
- **WHEN** entry数、各entryのuncompressed size、合計sizeがすべて復元resource policy内である
- **THEN** システムは既存のZIP entry書き込みと成功判定を継続する
