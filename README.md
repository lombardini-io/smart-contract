# Lombardini

## Crypto loans in WBTC for WAVES via DApp

1. A user can borrow WBTC tokens for WAVES for by calling `borrow` function and attaching WAVES to it.
The DAPP will transfer WBTC to user instantly on a oracle-specified rate.

2. After that, user can get his(or her) WAVES back by returning the loan, calling `buyBack` function and attaching WBTC. If returned during grace period, no interest rate is applied. During interest period, an interest rate will be applied, meanin user will have to return more BTC. Until the end of a loan the assets are frozen in the DAPP. A user can always discard the loan, keeping WBTC.

3. If the user doesn't return the loan during grace and freeze period, the unclaimed deposit is distributed between lenders.

## Roles

### User

 - Users can get a loan in WBTC for a WAVES deposit
 - If User returns deposit during grace period, the deposit is returned
 - If User returns deposit during interest period he needs to return additional % of BTC to get deposit back
 - If User doesn't return during grace period, anyone can close his loan, the contract keeps the deposit
 - User can discard the loan at any time, by doing this user gives up his deposit

### Lender

 - Lender can deposit exactly 10 WBTC to participate in lending.
 - Total Lenders amount is limited to 10
 - Upon lend closing, profits or deposit is distributed between lenders of the loan
 - Profits can be withdrawn to lender account by him or anyone else
 - Lender can't withdraw until all loans his stake participates in are closed
 - Lender can disable his participation in further loans

### Administrator

 - Administrator can setup loan parameters
 - Administrator can allow/deny new loans and new deposits
 - Administrator can optionally assign himself to get revenue share 

### Oracle
 
  - posts rate to his account to integer `waves_btc_8` key, satoshi per 1 WAVES

## API Spec

1. `init(owner: String, token: String, oracle: String, maxRate: Int, discount: Int, grace: Int, interest: Int, burndown: Int, serviceFee: Int, lendSize: Int)` - initializes the contract with 
 - owner(=admin)
 - oracle address
 - maxRate: if oracle posts a value greater than maxValue, it's considered suspicious. if oracle value is suspicious, it's impossible to start a new loan.
 - discount: BTC are disposed to user based on oracleRate, but with discount factor. if rate is 10k and discount is 80, the user will recieve not 10k sat for 1 WAVES, but only 8k sat.
 - grace: grace period for new loans
 - interest: interest period for new loans
 - burnown: burndown period for new loans. this is an artificial period used for interest calculation(math below)
 - serviceFee: when there's an unclaimed deposit/earned interest, the owner takes a share of serviceFee % of it
 - lendSize: # BTC required to become lender


2. `func updateParams(oracle: String, maxRate: Int, discount: Int, grace: Int, interest: Int, burndown: Int, serviceFee: Int, lendSize: Int)` - updates contract parameters, can be invoked by owner(=admin) only. params description is the same as for `init` method

3. `func borrow()` - borrows tokens, can be invoked by anyone who doesn't have an open loan. The parameters of the loan are fixed for the deal, e.g. further calls of `updateParams` do not affect the lend. The lenders are all currently enabled lenders.

4. `func buyBack()` - buys back, can be invoked by a user with an open loan. 

During grace period, no additional BTC is required.
During interest period:
Given loanSize,
 `profit = loanSize * (current_height - end_of_grace_height)/(end_of_burndown_height - end_of_grace_height))`
 `loanSize + profit` of BTC is required to return.
For both periods Transactions returning less are discarded, transactions returning more are accepted and the difference between required amount and actual amount is returned to the user.
After interest period ends, it's impossible to buyBack().
Admiin takes serviceFee % of profit, the rest is distributed between lenders for the deal.

5. `func discard()` - user can discard an open loan at any time, keeping WBTC and discarding the deposit, uncleaimed deposit is redistributed between lenders of the lend and the administrator: Admiin takes serviceFee % of it, the rest is distributed between lenders for the deal.

6. `func closeExpiredFor(address:String)` - an expired loan(after the end of interest period) can be closed by anyone. the effect is the same as of `discard` function. 

7. `func sendProfit(lender: String)`  - anyone can send accumulated profits and unclaimed deposits to an account. if the account is lender or administrator who has unclaimed assets, they will be sent, otherwise 0 tokens will be sent, but transaction will be c

8. `func takeProfit()` - if the account is lender or administrator who has unclaimed assets, they will be sent.

9. `func enableLending(b: Boolean)` - lender can pause/unpause participating in new lends.

10. `func depositBtc()` - lender can deposit 10 WBTC. lender becomes enabled by default.

11. `func withdrawBtc()` - lender can withdraw WBTC if no open loans are associated for him

12. `func enableDepositBtc(b: Boolean)` - admin function to enable/disable new deposits

13. `func enableNewLoans(b: Boolean)` - admin function to enable/disable new loans

### Known issues

1. The dAPP should not participate in any role(user, admin, owner, oracle).

2. if a lender will participate in a lot of failed lends, his stake can be smaller that the other participants. there should be a threshhold on that.

3. verifier is not defined, but that's for the better. makes management, further migration possible.
