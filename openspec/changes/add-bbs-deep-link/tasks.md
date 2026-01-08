## 1. Implementation
- [ ] 1.1 既存のURL解析/遷移ロジックを確認し、Deep Linkの正規化とルーティング方針を整理する
- [ ] 1.2 AndroidManifestにhttp/httpsのDeep Link intent-filterを追加する（対象ドメインを含む）
- [ ] 1.3 Deep Link受信処理を追加し、httpsに正規化したURLを板/スレ遷移に解決する
- [ ] 1.4 未対応URL（dat形式/解析不能）でエラートーストを表示する
- [ ] 1.5 Deep Link解析の単体テストを追加する（可能な範囲で）
- [ ] 1.6 ビルドとユニットテストを実行し、成功を確認する
