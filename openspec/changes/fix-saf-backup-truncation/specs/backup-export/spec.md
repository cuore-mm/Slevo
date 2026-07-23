## ADDED Requirements

### Requirement: 既存バックアップ出力先の truncate
システムは Storage Access Framework で選択された URI へバックアップ ZIP を書き込むとき、write-only truncate mode を明示して保存先を開かなければならない（MUST）。truncate 対応 mode で保存先を開けない場合、システムは非 truncate mode へフォールバックせず、バックアップ作成を成功扱いしてはならない（MUST NOT）。

#### Scenario: 既存ファイルより小さいバックアップで上書きする
- **GIVEN** 選択された URI に新しいバックアップ ZIP より大きい既存内容がある
- **WHEN** システムが同じ URI へ新しいバックアップ ZIP を書き込む
- **THEN** システムは write-only truncate mode で保存先を開く
- **AND** SAF mode contract に従う provider では、出力に新しい ZIP の終端より後の旧内容を残さない

#### Scenario: provider が truncate mode contract に違反する
- **GIVEN** SAF provider が write-only truncate mode を受理する
- **WHEN** provider が mode contract に反して既存内容を truncate しない
- **THEN** provider 固有の契約違反による旧末尾の除去はシステムの保証対象外とする

#### Scenario: 新しい保存先へバックアップを書き込む
- **GIVEN** 選択された URI に既存内容がない
- **WHEN** システムがバックアップ ZIP を書き込む
- **THEN** システムは write-only truncate mode で保存先を開き、既存の ZIP 成功条件を満たした場合だけ作成成功を返す

#### Scenario: provider が truncate 対応 mode を拒否する
- **WHEN** SAF provider が write-only truncate mode で保存先を開く要求を拒否する
- **THEN** システムは非 truncate mode で再試行せず、バックアップ作成を失敗扱いにする

#### Scenario: truncate 対応 mode の open が null を返す
- **WHEN** write-only truncate mode による保存先 open が output stream を返さない
- **THEN** システムはバックアップ作成を失敗扱いにし、成功表示を行わない
