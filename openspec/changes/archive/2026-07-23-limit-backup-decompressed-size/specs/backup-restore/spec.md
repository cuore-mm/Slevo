## ADDED Requirements

### Requirement: バックアップ展開resourceの制限
システムはバックアップZIPを読み取るとき、各entryの実際の展開byte数、全entryの合計展開byte数、およびentry数を制限しなければならない（MUST）。production defaultはmanifest 64 KiB、DB 256 MiB、settings 1 MiB、tabs 64 KiB、cookies 8 MiB、合計272 MiB、file/directoryを含むentry数7件とする。1 KiBは1024 bytes、1 MiBは1024×1024 bytesとし、各size上限はinclusiveとする。ZIP headerの申告sizeだけを最終判定に使用してはならない（MUST NOT）。

#### Scenario: DB entryが上限ちょうどである
- **WHEN** `database/slevo.db`の実際の展開byte数がDB entry上限と等しい
- **THEN** システムはsizeを理由にバックアップを拒否しない

#### Scenario: DB entryが上限を1 byte超える
- **WHEN** `database/slevo.db`の実際の展開byte数がDB entry上限を1 byte超える
- **THEN** システムは超過byteを書き込まず、バックアップを無効として扱い、部分的な一時DBを削除する

#### Scenario: JSON entryが上限を超える
- **WHEN** manifest、settings、tabs、またはcookies entryの実際の展開byte数が対応する上限を超える
- **THEN** システムは上限を超えるbyte配列を保持せず、バックアップを無効として扱う

#### Scenario: 合計展開sizeが上限を超える
- **GIVEN** 各entryの展開byte数は個別上限以下である
- **WHEN** 全entryの合計展開byte数が合計上限を超える
- **THEN** システムは超過を検出したentryで読み取りを中止し、バックアップを無効として扱う

#### Scenario: 合計展開sizeが上限ちょうどである
- **WHEN** 全entryの合計展開byte数が272 MiBまたは注入されたpolicyの合計上限と等しい
- **THEN** システムは合計sizeを理由にバックアップを拒否しない

#### Scenario: 高圧縮entryの申告sizeが小さい
- **GIVEN** ZIP headerの申告sizeまたは圧縮sizeが上限以下である
- **WHEN** streaming中に計測した実際の展開byte数が上限を超える
- **THEN** システムは実測値に基づいてバックアップを無効として扱う

#### Scenario: ZIP entry数が上限を超える
- **WHEN** fileとdirectoryを含むZIP entry数が上限を超える
- **THEN** システムは上限を超えたentryの内容を展開せず、バックアップを無効として扱う

#### Scenario: ZIP entry数が上限ちょうどである
- **WHEN** fileとdirectoryを含むZIP entry数が7件または注入されたpolicyの上限と等しい
- **THEN** システムはentry数を理由にバックアップを拒否しない

#### Scenario: resource limit内の既存バックアップを読み取る
- **WHEN** バックアップのentry数、各展開size、合計展開sizeがすべて上限以下である
- **THEN** システムは既存のpath、manifest、JSON、およびDB validationを継続する

### Requirement: Resource limit超過時の安全なcleanup
システムは一時DB作成後の処理が成功previewへのownership transfer以外で終了した場合、resource limit超過、I/O失敗、malformed ZIP、validation失敗、cancellationを含む原因にかかわらず作成途中の一時DBを残してはならない（MUST NOT）。不成功時に成功previewまたは復元準備完了を返してはならない（MUST NOT）。

#### Scenario: DB streaming途中でsize limitを超える
- **WHEN** DB entryを一時ファイルへ書き込んでいる途中でentry上限または合計上限を超える
- **THEN** システムはstreamをcloseし、部分的な一時DBを削除し、無効なバックアップを返す

#### Scenario: JSON読み取り途中でsize limitを超える
- **WHEN** JSON entryをmemoryへ読み込んでいる途中でentry上限または合計上限を超える
- **THEN** システムはoversized JSONをparseせず、無効なバックアップを返す

#### Scenario: 一時DB書き込みがI/O失敗する
- **WHEN** DB entryの一時ファイル書き込みがsize limit到達前にI/O例外で失敗する
- **THEN** システムは部分的な一時DBを削除し、既存の失敗結果を返す

#### Scenario: 一時DB作成後に後続validationが失敗する
- **WHEN** DB entryの一時ファイル作成後にZIP、manifest、JSON、またはDB validationが失敗する
- **THEN** システムは一時DBを削除し、成功previewを返さない

#### Scenario: 一時DB作成後に処理がcancelされる
- **WHEN** DB entryの一時ファイル作成後に読み取り処理がcancelされる
- **THEN** システムは一時DBを削除してから既存のcancellation semanticsを継続する
