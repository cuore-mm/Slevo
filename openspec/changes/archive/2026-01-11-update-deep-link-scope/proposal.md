# Change: Deep Link の受付範囲をPC/itestの板・スレに限定

## Why
Deep Link の受付条件が広く、アプリが対応想定していないURLまで受け付けてしまうため、実際に対応するURLパターンへ限定する。

## What Changes
- Deep Link 受付を PC 版と itest 版の板/スレ 4 種に限定する
- 2ch.sc ドメインでは itest 版の URL を許可しない

## Impact
- Affected specs: handle-deep-link
- Affected code: Deep Link の Intent フィルタ/判定ロジック、Deep Link テスト
