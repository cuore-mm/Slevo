# default-5ch-menu Specification

## Purpose
TBD - created by archiving change support-5ch-io. Update Purpose after archive.
## Requirements
### Requirement: 既定BBSMenuを5ch.ioから取得する
システムは5chの既定BBSMenuを参照する場合、`5ch.io` 側のBBSMenu URLを使用することを SHALL 要求する。

#### Scenario: 既定BBSMenuから板hostを解決する
- **WHEN** システムがDBに存在しないboardKeyのhostを既定BBSMenuから補完する
- **THEN** システムは `https://menu.5ch.io/bbsmenu.html` を取得先として使用する

#### Scenario: 既定サービスを追加または更新する
- **WHEN** システムが既定の5chサービスを追加または更新する
- **THEN** システムは `5ch.io` 側のBBSMenu URLを既定値として扱う
