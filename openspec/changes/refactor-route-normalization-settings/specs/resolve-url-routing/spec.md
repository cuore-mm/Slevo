## ADDED Requirements

### Requirement: URL解析結果から生成したrouteの正規化境界
システムは共通URLリゾルバの解析結果から板/スレrouteを生成した後、ナビゲーション関数へ渡す前に永続化済み設定値に基づくroute正規化を適用することを SHALL 要求する。共通URLリゾルバ自体は入力URLの解析に専念し、設定値参照や `5ch.net` から `5ch.io` への変換を行わないことを SHALL 要求する。

#### Scenario: PC版スレURL解析後にrouteを正規化する
- **WHEN** 共通URLリゾルバが `https://agree.5ch.net/test/read.cgi/operate/1234567890/` を `Thread` として解析する
- **THEN** システムは解析結果からrouteを生成した後、永続化済み設定値に基づいて `boardUrl` を正規化してからナビゲーションへ渡す

#### Scenario: 共通URLリゾルバは設定値を参照しない
- **WHEN** 共通URLリゾルバがURL文字列を解析する
- **THEN** システムは解析処理内で `5ch.net` を `5ch.io` に変換せず、設定値も参照しない
